package com.fundit.payment.application.refund;

import com.fundit.payment.application.event.PaymentEventPublisher;
import com.fundit.payment.application.notification.PaymentNotificationPublisher;
import com.fundit.payment.application.payment.TossApiException;
import com.fundit.payment.application.payment.TossPaymentsClient;
import com.fundit.payment.application.settlement.SettlementHoldService;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentMethod;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.payment.PaymentStatus;
import com.fundit.payment.domain.refund.RefundRequest;
import com.fundit.payment.domain.refund.RefundRequestRepository;
import com.fundit.payment.domain.refund.RefundRequestStatus;
import com.fundit.payment.domain.refund.RefundTriggerType;
import com.fundit.payment.infrastructure.persistence.payment.PaymentCancellationJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundExecutionServiceUnitTest {

    private static final UUID FUNDING_ID = new UUID(0L, 1024L);
    private static final UUID MEMBER_ID = UUID.randomUUID();

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private TossPaymentsClient tossPaymentsClient;
    @Mock
    private PaymentCancellationJpaRepository paymentCancellationJpaRepository;
    @Mock
    private RefundRequestRepository refundRequestRepository;
    @Mock
    private PaymentEventPublisher paymentEventPublisher;
    @Mock
    private PaymentNotificationPublisher paymentNotificationPublisher;
    @Mock
    private SettlementHoldService settlementHoldService;

    private RefundExecutionService refundExecutionService;

    @BeforeEach
    void setUp() {
        refundExecutionService = new RefundExecutionService(paymentRepository, tossPaymentsClient,
                paymentCancellationJpaRepository, refundRequestRepository, paymentEventPublisher,
                paymentNotificationPublisher, settlementHoldService,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), 5L);
    }

    private Payment completedPayment() {
        Payment payment = Payment.create(FUNDING_ID, MEMBER_ID, "fundit-order-1", 89_000L, "테스트 주문", List.of(7L), "idem");
        payment.markCompleted("pay_key_1", "secret_1", PaymentMethod.CARD, null, Instant.now());
        return payment;
    }

    private Payment pendingPayment() {
        return Payment.create(FUNDING_ID, MEMBER_ID, "fundit-order-1", 89_000L, "테스트 주문", null, "idem");
    }

    private static TossPaymentsClient.TossPaymentLookup lookup(String status, long amount,
                                                               TossPaymentsClient.TossCancelResult... cancels) {
        return new TossPaymentsClient.TossPaymentLookup(status, new TossPaymentsClient.TossPaymentResult(
                "pay_key_1", "fundit-order-1", "secret_1", "카드", null, Instant.now(), amount), List.of(cancels));
    }

    private void saveReturnsArgument() {
        when(refundRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void 전액취소에_성공하면_결제가_CANCELLED로_전환되고_보류금이_해제된다() {
        // given
        Payment payment = completedPayment();
        when(paymentRepository.findCompletedOrCancelledByFundingId(FUNDING_ID)).thenReturn(Optional.of(payment));
        when(tossPaymentsClient.cancel("pay_key_1", 89_000L, "구매자 단순변심 참여 취소"))
                .thenReturn(new TossPaymentsClient.TossCancelResult("tx_1", Instant.now(), 89_000L));
        saveReturnsArgument();

        // when
        var result = refundExecutionService.executeFullRefund(FUNDING_ID, RefundTriggerType.SIMPLE_CHANGE_OF_MIND,
                "구매자 단순변심 참여 취소");

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(result.fullRefund()).isTrue();
        assertThat(result.status()).isEqualTo("COMPLETED");
        verify(settlementHoldService).releaseToRefund(payment.getId());
        verify(paymentEventPublisher).publishRefundCompleted(any());
        verify(paymentCancellationJpaRepository).save(any());
        ArgumentCaptor<PaymentNotificationPublisher.RefundStatusChangedEvent> notificationCaptor =
                ArgumentCaptor.forClass(PaymentNotificationPublisher.RefundStatusChangedEvent.class);
        verify(paymentNotificationPublisher).publishRefundStatusChanged(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().memberId()).isEqualTo(MEMBER_ID);
        assertThat(notificationCaptor.getValue().status())
                .isEqualTo(PaymentNotificationPublisher.RefundNotificationStatus.COMPLETED);
    }

    @Test
    void 토스를_부르기_전에_취소_요청을_먼저_기록하고_사유가_환불_내역에도_저장된다() {
        // given
        Payment payment = completedPayment();
        when(paymentRepository.findCompletedOrCancelledByFundingId(FUNDING_ID)).thenReturn(Optional.of(payment));
        when(tossPaymentsClient.cancel("pay_key_1", 89_000L, "[CHANGE_OF_MIND] 다른 상품 구매"))
                .thenReturn(new TossPaymentsClient.TossCancelResult("tx_1", Instant.now(), 89_000L));
        saveReturnsArgument();

        // when
        refundExecutionService.executeFullRefund(FUNDING_ID, RefundTriggerType.SIMPLE_CHANGE_OF_MIND,
                "[CHANGE_OF_MIND] 다른 상품 구매");

        // then — 요청 기록(트랜잭션 1) → 토스 취소 → 확정(트랜잭션 2) 순서
        ArgumentCaptor<RefundRequest> captor = ArgumentCaptor.forClass(RefundRequest.class);
        InOrder order = inOrder(refundRequestRepository, tossPaymentsClient);
        order.verify(refundRequestRepository).save(captor.capture());
        order.verify(tossPaymentsClient).cancel("pay_key_1", 89_000L, "[CHANGE_OF_MIND] 다른 상품 구매");
        order.verify(refundRequestRepository).save(any());
        assertThat(captor.getValue().getReasonDetail()).isEqualTo("[CHANGE_OF_MIND] 다른 상품 구매");
        assertThat(captor.getValue().getStatus()).isEqualTo(RefundRequestStatus.COMPLETED);
    }

    /** stale 기준(5분)이 지난 취소 요청 — 앞선 호출이 멈춘 것으로 보고 이어받는다. */
    private static RefundRequest staleCancelRequest(UUID paymentId) {
        return RefundRequest.requestCancel(RefundTriggerType.SIMPLE_CHANGE_OF_MIND, FUNDING_ID, paymentId, 89_000L, "사유")
                .toBuilder().id(5L).cancelRequestedAt(Instant.now().minusSeconds(600)).build();
    }

    @Test
    void 방금_요청된_취소가_진행_중이면_토스를_다시_부르지_않고_처리_중으로_답한다() {
        // given — 같은 이벤트가 중복 수신돼, 앞선 호출이 아직 토스 응답을 기다리는 중이다
        Payment payment = completedPayment();
        RefundRequest inFlight = RefundRequest.requestCancel(RefundTriggerType.SIMPLE_CHANGE_OF_MIND, FUNDING_ID,
                payment.getId(), 89_000L, "사유").toBuilder().id(5L).build();
        when(paymentRepository.findCompletedOrCancelledByFundingId(FUNDING_ID)).thenReturn(Optional.of(payment));
        when(refundRequestRepository.findCancelInFlightByPaymentId(payment.getId())).thenReturn(Optional.of(inFlight));

        // when
        var result = refundExecutionService.executeFullRefund(FUNDING_ID, RefundTriggerType.SIMPLE_CHANGE_OF_MIND, "사유");

        // then
        assertThat(result.status()).isEqualTo("PROCESSING");
        assertThat(result.refundRequestId()).isEqualTo(5L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verifyNoInteractions(tossPaymentsClient, paymentEventPublisher);
    }

    @Test
    void 멈춘_취소_요청이라도_다른_호출이_먼저_선점했으면_토스를_부르지_않는다() {
        // given — 대사 배치가 같은 요청을 먼저 이어받았다
        Payment payment = completedPayment();
        RefundRequest stale = staleCancelRequest(payment.getId());
        when(paymentRepository.findCompletedOrCancelledByFundingId(FUNDING_ID)).thenReturn(Optional.of(payment));
        when(refundRequestRepository.findCancelInFlightByPaymentId(payment.getId())).thenReturn(Optional.of(stale));
        when(refundRequestRepository.claimCancelRequest(eq(5L), any(), any())).thenReturn(false);

        // when
        var result = refundExecutionService.executeFullRefund(FUNDING_ID, RefundTriggerType.SIMPLE_CHANGE_OF_MIND, "사유");

        // then
        assertThat(result.status()).isEqualTo("PROCESSING");
        verifyNoInteractions(tossPaymentsClient, paymentEventPublisher);
    }

    @Test
    void 진행_중인_취소_요청이_있으면_이어받고_토스에_이미_일어난_취소가_있으면_다시_취소하지_않는다() {
        // given — 이전 시도에서 토스 취소는 됐는데 확정이 실패했다(stale 기준 경과)
        Payment payment = completedPayment();
        RefundRequest inFlight = staleCancelRequest(payment.getId());
        when(paymentRepository.findCompletedOrCancelledByFundingId(FUNDING_ID)).thenReturn(Optional.of(payment));
        when(refundRequestRepository.findCancelInFlightByPaymentId(payment.getId())).thenReturn(Optional.of(inFlight));
        when(refundRequestRepository.claimCancelRequest(eq(5L), any(), any())).thenReturn(true);
        when(tossPaymentsClient.lookup("pay_key_1")).thenReturn(lookup("CANCELED", 89_000L,
                new TossPaymentsClient.TossCancelResult("tx_1", Instant.now(), 89_000L)));
        saveReturnsArgument();

        // when
        var result = refundExecutionService.executeFullRefund(FUNDING_ID, RefundTriggerType.SIMPLE_CHANGE_OF_MIND, "사유");

        // then
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(tossPaymentsClient, never()).cancel(any(), anyLong(), any());
        verify(paymentCancellationJpaRepository).existsByPgTransactionKey("tx_1");
    }

    @Test
    void 토스가_ALREADY_CANCELED_PAYMENT로_답하면_실패가_아니라_조회한_취소로_확정한다() {
        // given
        Payment payment = completedPayment();
        when(paymentRepository.findCompletedOrCancelledByFundingId(FUNDING_ID)).thenReturn(Optional.of(payment));
        when(tossPaymentsClient.cancel("pay_key_1", 89_000L, "사유"))
                .thenThrow(new TossApiException(TossApiException.ALREADY_CANCELED_PAYMENT, "이미 취소된 결제입니다."));
        when(tossPaymentsClient.lookup("pay_key_1")).thenReturn(lookup("CANCELED", 89_000L,
                new TossPaymentsClient.TossCancelResult("tx_1", Instant.now(), 89_000L)));
        saveReturnsArgument();

        // when
        var result = refundExecutionService.executeFullRefund(FUNDING_ID, RefundTriggerType.SIMPLE_CHANGE_OF_MIND, "사유");

        // then
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(paymentEventPublisher).publishRefundCompleted(any());
        verify(refundRequestRepository, never()).delete(any());
    }

    @Test
    void 이미_취소된_결제면_토스를_다시_호출하지_않고_멱등_처리한다() {
        // given
        Payment payment = completedPayment();
        payment.markCancelled();
        when(paymentRepository.findCompletedOrCancelledByFundingId(FUNDING_ID)).thenReturn(Optional.of(payment));

        // when
        var result = refundExecutionService.executeFullRefund(FUNDING_ID, RefundTriggerType.GOAL_FAILED_AUTO, "사유");

        // then
        assertThat(result.status()).isEqualTo("ALREADY_PROCESSED");
        verifyNoInteractions(tossPaymentsClient);
        verifyNoInteractions(paymentEventPublisher);
    }

    @Test
    void 판매자_승인_부분취소는_전액이_아니므로_보류금을_해제하지_않는다() {
        // given
        Payment payment = completedPayment();
        RefundRequest refundRequest = RefundRequest.requestAfterShipment(RefundTriggerType.RETURN_CHANGE_OF_MIND,
                FUNDING_ID, payment.getId(), UUID.randomUUID(), "변심", List.of());
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(tossPaymentsClient.cancel("pay_key_1", 84_000L, "반품 승인(반품비 차감)"))
                .thenReturn(new TossPaymentsClient.TossCancelResult("tx_2", Instant.now(), 84_000L));
        saveReturnsArgument();

        // when
        var result = refundExecutionService.executeApprovedRefund(refundRequest, 84_000L, "반품 승인(반품비 차감)");

        // then
        assertThat(result.fullRefund()).isFalse();
        assertThat(refundRequest.getStatus()).isEqualTo(RefundRequestStatus.COMPLETED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED); // 부분취소라 CANCELLED로 바뀌지 않음
        verify(settlementHoldService, never()).releaseToRefund(any());
    }

    @Nested
    class 결제_전_참여_취소 {

        @Test
        void 토스에_결제가_없으면_대기_결제를_실패_처리하고_취소는_호출하지_않는다() {
            // given — 토스에 결제가 없다(인증 전)
            Payment pending = pendingPayment();
            when(paymentRepository.findCompletedOrCancelledByFundingId(FUNDING_ID)).thenReturn(Optional.empty());
            when(paymentRepository.findPendingByFundingId(FUNDING_ID)).thenReturn(Optional.of(pending));
            when(tossPaymentsClient.lookupByOrderId("fundit-order-1")).thenReturn(Optional.empty());
            when(paymentRepository.failIfPending(pending.getId())).thenReturn(true);

            // when
            var result = refundExecutionService.executeFullRefund(FUNDING_ID, RefundTriggerType.SIMPLE_CHANGE_OF_MIND, "사유");

            // then — 결제창에서 뒤늦게 확정되지 않도록 조건부로 FAILED 처리한다
            assertThat(result.status()).isEqualTo("PENDING_PAYMENT_CLOSED");
            verify(tossPaymentsClient, never()).cancel(any(), anyLong(), any());
            verifyNoInteractions(refundRequestRepository, paymentEventPublisher);
        }

        @Test
        void 토스에서_승인이_진행_중이면_닫지_않고_취소_요청을_남겨_대사_배치로_넘긴다() {
            // given — 인증은 끝났고 우리 승인 호출이 진행 중일 수 있다
            Payment pending = pendingPayment();
            when(paymentRepository.findCompletedOrCancelledByFundingId(FUNDING_ID)).thenReturn(Optional.empty());
            when(paymentRepository.findPendingByFundingId(FUNDING_ID)).thenReturn(Optional.of(pending));
            when(tossPaymentsClient.lookupByOrderId("fundit-order-1")).thenReturn(Optional.of(lookup("IN_PROGRESS", 89_000L)));
            saveReturnsArgument();

            // when
            var result = refundExecutionService.executeFullRefund(FUNDING_ID, RefundTriggerType.SIMPLE_CHANGE_OF_MIND, "사유");

            // then
            assertThat(result.status()).isEqualTo("PENDING_PAYMENT_UNRESOLVED");
            assertThat(pending.getStatus()).isEqualTo(PaymentStatus.PENDING);
            verify(paymentRepository, never()).failIfPending(any());
            ArgumentCaptor<RefundRequest> captor = ArgumentCaptor.forClass(RefundRequest.class);
            verify(refundRequestRepository).save(captor.capture());
            assertThat(captor.getValue().isCancelInFlight()).isTrue();
            assertThat(captor.getValue().getCancelAmount()).isEqualTo(89_000L);
        }

        @Test
        void 닫기_직전에_승인이_먼저_끝났으면_덮어쓰지_않고_종료한다() {
            // given
            Payment pending = pendingPayment();
            when(paymentRepository.findCompletedOrCancelledByFundingId(FUNDING_ID)).thenReturn(Optional.empty());
            when(paymentRepository.findPendingByFundingId(FUNDING_ID)).thenReturn(Optional.of(pending));
            when(tossPaymentsClient.lookupByOrderId("fundit-order-1")).thenReturn(Optional.empty());
            when(paymentRepository.failIfPending(pending.getId())).thenReturn(false);

            // when
            var result = refundExecutionService.executeFullRefund(FUNDING_ID, RefundTriggerType.SIMPLE_CHANGE_OF_MIND, "사유");

            // then
            assertThat(result.status()).isEqualTo("PENDING_PAYMENT_UNRESOLVED");
            verify(paymentRepository, never()).save(any());
        }

        @Test
        void 결과_불명으로_대기_중이던_결제가_토스에선_승인돼_있으면_완료로_맞춘_뒤_전액_취소한다() {
            // given — 승인 응답이 5xx였지만 토스에선 승인된 결제
            Payment pending = pendingPayment();
            when(paymentRepository.findCompletedOrCancelledByFundingId(FUNDING_ID)).thenReturn(Optional.empty());
            when(paymentRepository.findPendingByFundingId(FUNDING_ID)).thenReturn(Optional.of(pending));
            when(tossPaymentsClient.lookupByOrderId("fundit-order-1")).thenReturn(Optional.of(lookup("DONE", 89_000L)));
            when(tossPaymentsClient.cancel("pay_key_1", 89_000L, "사유"))
                    .thenReturn(new TossPaymentsClient.TossCancelResult("tx_1", Instant.now(), 89_000L));
            saveReturnsArgument();

            // when
            refundExecutionService.executeFullRefund(FUNDING_ID, RefundTriggerType.SIMPLE_CHANGE_OF_MIND, "사유");

            // then — 돈이 빠진 채 FAILED로 닫히지 않고 환불된다
            assertThat(pending.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            verify(paymentEventPublisher).publishRefundCompleted(any());
            verify(paymentEventPublisher, never()).publishPaymentCompleted(any());
        }
    }

    @Nested
    class 대사_배치 {

        private final Instant before = Instant.now();

        @BeforeEach
        void 선점은_성공한다() {
            when(refundRequestRepository.claimCancelRequest(any(), eq(before), any())).thenReturn(true);
        }

        @Test
        void 토스에선_취소됐는데_확정되지_않은_건을_조회한_취소로_확정한다() {
            // given
            Payment payment = completedPayment();
            RefundRequest stuck = RefundRequest.requestCancel(RefundTriggerType.GOAL_FAILED_AUTO, FUNDING_ID,
                    payment.getId(), 89_000L, "목표금액 미달 자동환불");
            when(refundRequestRepository.findCancelsRequestedBefore(before, 50)).thenReturn(List.of(stuck));
            when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
            when(tossPaymentsClient.lookup("pay_key_1")).thenReturn(lookup("CANCELED", 89_000L,
                    new TossPaymentsClient.TossCancelResult("tx_1", Instant.now(), 89_000L)));
            saveReturnsArgument();

            // when
            int resolved = refundExecutionService.reconcileCancelsRequestedBefore(before, 50);

            // then — 해결한 건은 미루지 않는다
            assertThat(resolved).isEqualTo(1);
            assertThat(stuck.getStatus()).isEqualTo(RefundRequestStatus.COMPLETED);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            verify(refundRequestRepository, never()).deferCancelRequest(any(), any());
            verify(settlementHoldService).releaseToRefund(payment.getId());
            verify(paymentEventPublisher).publishRefundCompleted(any());
            verify(tossPaymentsClient, never()).cancel(any(), anyLong(), any());
        }

        @Test
        void 토스에_취소가_없으면_다시_취소한_뒤_확정한다() {
            // given — 토스 호출이 5xx로 끝났고 실제로는 취소되지 않았다
            Payment payment = completedPayment();
            RefundRequest stuck = RefundRequest.requestCancel(RefundTriggerType.SIMPLE_CHANGE_OF_MIND, FUNDING_ID,
                    payment.getId(), 89_000L, "사유");
            when(refundRequestRepository.findCancelsRequestedBefore(before, 50)).thenReturn(List.of(stuck));
            when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
            when(tossPaymentsClient.lookup("pay_key_1")).thenReturn(lookup("DONE", 89_000L));
            when(tossPaymentsClient.cancel("pay_key_1", 89_000L, "사유"))
                    .thenReturn(new TossPaymentsClient.TossCancelResult("tx_1", Instant.now(), 89_000L));
            saveReturnsArgument();

            // when
            int resolved = refundExecutionService.reconcileCancelsRequestedBefore(before, 50);

            // then
            assertThat(resolved).isEqualTo(1);
            assertThat(stuck.getStatus()).isEqualTo(RefundRequestStatus.COMPLETED);
        }

        @Test
        void 대기_결제가_토스에선_승인돼_있으면_완료로_맞춘_뒤_전액_취소한다() {
            // given
            Payment pending = pendingPayment();
            RefundRequest stuck = RefundRequest.requestCancel(RefundTriggerType.SIMPLE_CHANGE_OF_MIND, FUNDING_ID,
                    pending.getId(), 89_000L, "사유");
            when(refundRequestRepository.findCancelsRequestedBefore(before, 50)).thenReturn(List.of(stuck));
            when(paymentRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
            when(tossPaymentsClient.lookupByOrderId("fundit-order-1")).thenReturn(Optional.of(lookup("DONE", 89_000L)));
            when(tossPaymentsClient.cancel("pay_key_1", 89_000L, "사유"))
                    .thenReturn(new TossPaymentsClient.TossCancelResult("tx_1", Instant.now(), 89_000L));
            saveReturnsArgument();

            // when
            int resolved = refundExecutionService.reconcileCancelsRequestedBefore(before, 50);

            // then
            assertThat(resolved).isEqualTo(1);
            assertThat(pending.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            assertThat(stuck.getStatus()).isEqualTo(RefundRequestStatus.COMPLETED);
        }

        @Test
        void 대기_결제가_승인될_수_없는_상태면_실패_처리하고_취소_요청을_정리한다() {
            // given
            Payment pending = pendingPayment();
            RefundRequest stuck = RefundRequest.builder().id(3L).paymentId(pending.getId())
                    .triggerType(RefundTriggerType.SIMPLE_CHANGE_OF_MIND).status(RefundRequestStatus.PROCESSING)
                    .cancelAmount(89_000L).build();
            when(refundRequestRepository.findCancelsRequestedBefore(before, 50)).thenReturn(List.of(stuck));
            when(paymentRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
            when(tossPaymentsClient.lookupByOrderId("fundit-order-1")).thenReturn(Optional.of(lookup("EXPIRED", 89_000L)));
            when(paymentRepository.failIfPending(pending.getId())).thenReturn(true);

            // when
            int resolved = refundExecutionService.reconcileCancelsRequestedBefore(before, 50);

            // then
            assertThat(resolved).isEqualTo(1);
            verify(refundRequestRepository).delete(3L);
            verify(tossPaymentsClient, never()).cancel(any(), anyLong(), any());
        }

        @Test
        void 대기_결제를_판단할_수_없으면_다음_주기로_넘긴다() {
            // given
            Payment pending = pendingPayment();
            RefundRequest stuck = RefundRequest.requestCancel(RefundTriggerType.SIMPLE_CHANGE_OF_MIND, FUNDING_ID,
                    pending.getId(), 89_000L, "사유");
            when(refundRequestRepository.findCancelsRequestedBefore(before, 50)).thenReturn(List.of(stuck));
            when(paymentRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
            when(tossPaymentsClient.lookupByOrderId("fundit-order-1")).thenReturn(Optional.of(lookup("IN_PROGRESS", 89_000L)));

            // when
            int resolved = refundExecutionService.reconcileCancelsRequestedBefore(before, 50);

            // then — 해결 못 한 건은 요청 시각을 미뤄 다음 주기 목록의 뒤로 보낸다
            assertThat(resolved).isZero();
            assertThat(stuck.isCancelInFlight()).isTrue();
            verify(paymentRepository, never()).failIfPending(any());
            verify(refundRequestRepository, never()).delete(any());
            verify(refundRequestRepository).deferCancelRequest(any(), any());
        }
    }
}
