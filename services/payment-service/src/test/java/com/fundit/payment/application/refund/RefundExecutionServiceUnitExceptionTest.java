package com.fundit.payment.application.refund;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.DependencyFailureException;
import com.fundit.payment.application.event.PaymentEventPublisher;
import com.fundit.payment.application.notification.PaymentNotificationPublisher;
import com.fundit.payment.application.payment.TossApiException;
import com.fundit.payment.application.payment.TossPaymentsClient;
import com.fundit.payment.application.settlement.SettlementHoldService;
import com.fundit.payment.domain.PaymentErrorCode;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundExecutionServiceUnitExceptionTest {

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
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
    }

    private Payment completedPayment() {
        Payment payment = Payment.create(FUNDING_ID, MEMBER_ID, "fundit-order-1", 89_000L, "테스트 주문", null, "idem");
        payment.markCompleted("pay_key_1", "secret_1", PaymentMethod.CARD, null, Instant.now());
        return payment;
    }

    /** 저장하면 id가 붙은 것처럼 돌려준다 — 삭제 대상 id를 확인하려고 쓴다. */
    private void saveAssignsId() {
        when(refundRequestRepository.save(any())).thenAnswer(inv -> {
            RefundRequest request = inv.getArgument(0);
            return request.getId() == null ? request.toBuilder().id(11L).build() : request;
        });
    }

    @Test
    void 완료된_결제도_대기_결제도_없으면_NOT_FOUND_예외가_발생한다() {
        // given
        when(paymentRepository.findCompletedOrCancelledByFundingId(FUNDING_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> refundExecutionService.executeFullRefund(FUNDING_ID,
                RefundTriggerType.SIMPLE_CHANGE_OF_MIND, "사유"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    void 토스가_취소를_거절하면_PG_CANCEL_FAILED_예외가_발생하고_취소_요청은_지워진다() {
        // given
        Payment payment = completedPayment();
        when(paymentRepository.findCompletedOrCancelledByFundingId(FUNDING_ID)).thenReturn(Optional.of(payment));
        saveAssignsId();
        when(tossPaymentsClient.cancel(any(), any(Long.class), any()))
                .thenThrow(new TossApiException("REJECT_CARD_COMPANY", "카드사 거절"));

        // when & then
        assertThatThrownBy(() -> refundExecutionService.executeFullRefund(FUNDING_ID,
                RefundTriggerType.SIMPLE_CHANGE_OF_MIND, "사유"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.PG_CANCEL_FAILED));

        // then — 돈이 움직이지 않았으니 요청을 지우고, 확정 기록/이벤트는 전혀 없다
        verify(refundRequestRepository).delete(11L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(paymentCancellationJpaRepository, never()).save(any());
        verifyNoInteractions(paymentEventPublisher);
        verifyNoInteractions(settlementHoldService);
    }

    @Test
    void 토스_취소_거절시_대체계좌_대상_유형은_대체계좌_대기_상태로_전환한다() {
        // given
        Payment payment = completedPayment();
        when(paymentRepository.findCompletedOrCancelledByFundingId(FUNDING_ID)).thenReturn(Optional.of(payment));
        when(refundRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tossPaymentsClient.cancel(any(), any(Long.class), any()))
                .thenThrow(new TossApiException("REJECT_CARD_COMPANY", "카드사 거절"));

        // when
        var result = refundExecutionService.executeFullRefundOrAwaitAlternateAccount(FUNDING_ID,
                RefundTriggerType.GOAL_FAILED_AUTO, "목표 미달 자동환불");

        // then
        assertThat(result.status()).isEqualTo("AWAITING_ALTERNATE_ACCOUNT");
        ArgumentCaptor<RefundRequest> captor = ArgumentCaptor.forClass(RefundRequest.class);
        verify(refundRequestRepository, times(2)).save(captor.capture());
        RefundRequest saved = captor.getValue();
        assertThat(saved.getTriggerType()).isEqualTo(RefundTriggerType.GOAL_FAILED_AUTO);
        assertThat(saved.getStatus()).isEqualTo(RefundRequestStatus.REQUESTED);
        assertThat(saved.isCancelInFlight()).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED); // 취소되지 않고 그대로 유지
        ArgumentCaptor<PaymentNotificationPublisher.RefundStatusChangedEvent> notificationCaptor =
                ArgumentCaptor.forClass(PaymentNotificationPublisher.RefundStatusChangedEvent.class);
        verify(paymentNotificationPublisher).publishRefundStatusChanged(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().status())
                .isEqualTo(PaymentNotificationPublisher.RefundNotificationStatus.AWAITING_ALTERNATE_ACCOUNT);
    }

    @Test
    void 판매자_승인_후_토스가_거절하면_다시_결정할_수_있게_신청을_되돌린다() {
        // given
        Payment payment = completedPayment();
        RefundRequest refundRequest = RefundRequest.requestAfterShipment(RefundTriggerType.DEFECT, FUNDING_ID,
                payment.getId(), UUID.randomUUID(), "파손", List.of("url"));
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(refundRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tossPaymentsClient.cancel(any(), any(Long.class), any()))
                .thenThrow(new TossApiException("REJECT_CARD_COMPANY", "카드사 거절"));

        // when & then
        assertThatThrownBy(() -> refundExecutionService.executeApprovedRefund(refundRequest, 89_000L, "하자환불 승인"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.PG_CANCEL_FAILED));
        assertThat(refundRequest.getStatus()).isEqualTo(RefundRequestStatus.REQUESTED);
        verify(refundRequestRepository, never()).delete(any());
    }

    @Test
    void 확정_단계가_실패하면_예외가_전파되고_취소_요청은_그대로_남는다() {
        // given — 토스 취소는 성공했는데 아웃박스 적재가 실패했다
        Payment payment = completedPayment();
        when(paymentRepository.findCompletedOrCancelledByFundingId(FUNDING_ID)).thenReturn(Optional.of(payment));
        when(refundRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tossPaymentsClient.cancel("pay_key_1", 89_000L, "사유"))
                .thenReturn(new TossPaymentsClient.TossCancelResult("tx_1", Instant.now(), 89_000L));
        doThrow(new IllegalStateException("outbox down")).when(paymentEventPublisher).publishRefundCompleted(any());

        // when & then
        assertThatThrownBy(() -> refundExecutionService.executeFullRefund(FUNDING_ID,
                RefundTriggerType.SIMPLE_CHANGE_OF_MIND, "사유"))
                .isInstanceOf(IllegalStateException.class);

        // then — 요청을 지우거나 되돌리지 않는다(확정 트랜잭션만 롤백되고, 대사 배치가 토스 조회로 맞춘다)
        verify(refundRequestRepository, never()).delete(any());
        verify(tossPaymentsClient).cancel("pay_key_1", 89_000L, "사유");
    }

    @Test
    void 토스_응답이_불명확하면_예외를_던지고_취소_요청은_그대로_남는다() {
        // given
        Payment payment = completedPayment();
        when(paymentRepository.findCompletedOrCancelledByFundingId(FUNDING_ID)).thenReturn(Optional.of(payment));
        when(refundRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tossPaymentsClient.cancel("pay_key_1", 89_000L, "사유"))
                .thenThrow(new DependencyFailureException(new RuntimeException("timeout")));

        // when & then
        assertThatThrownBy(() -> refundExecutionService.executeFullRefund(FUNDING_ID,
                RefundTriggerType.SIMPLE_CHANGE_OF_MIND, "사유"))
                .isInstanceOf(DependencyFailureException.class);
        verify(refundRequestRepository, never()).delete(any());
        verify(paymentCancellationJpaRepository, never()).save(any());
    }

    @Test
    void 이미_취소됐다는데_확정할_취소를_찾지_못하면_실패로_굳히지_않고_예외를_던진다() {
        // given — 토스의 취소는 전부 로컬에 이미 기록돼 있다
        Payment payment = completedPayment();
        when(paymentRepository.findCompletedOrCancelledByFundingId(FUNDING_ID)).thenReturn(Optional.of(payment));
        when(refundRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tossPaymentsClient.cancel("pay_key_1", 89_000L, "사유"))
                .thenThrow(new TossApiException(TossApiException.ALREADY_CANCELED_PAYMENT, "이미 취소된 결제입니다."));
        when(tossPaymentsClient.lookup("pay_key_1")).thenReturn(new TossPaymentsClient.TossPaymentLookup("CANCELED",
                new TossPaymentsClient.TossPaymentResult("pay_key_1", "fundit-order-1", null, "카드", null, Instant.now(), 89_000L),
                List.of(new TossPaymentsClient.TossCancelResult("tx_old", Instant.now(), 89_000L))));
        when(paymentCancellationJpaRepository.existsByPgTransactionKey("tx_old")).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> refundExecutionService.executeFullRefund(FUNDING_ID,
                RefundTriggerType.SIMPLE_CHANGE_OF_MIND, "사유"))
                .isInstanceOf(DependencyFailureException.class);
        verify(refundRequestRepository, never()).delete(any());
    }

    @Test
    void 승인_대상_결제를_찾을_수_없으면_NOT_FOUND_예외가_발생한다() {
        // given
        var refundRequest = RefundRequest.requestAfterShipment(RefundTriggerType.DEFECT,
                FUNDING_ID, UUID.randomUUID(), UUID.randomUUID(), "파손", List.of("url"));
        when(paymentRepository.findById(refundRequest.getPaymentId())).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> refundExecutionService.executeApprovedRefund(refundRequest, 10_000L, "사유"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    void 대기_결제의_토스_조회가_실패하면_FAILED로_닫지_않고_취소_요청을_남긴다() {
        // given
        Payment pending = Payment.create(FUNDING_ID, MEMBER_ID, "fundit-order-1", 89_000L, "테스트 주문", null, "idem");
        when(paymentRepository.findCompletedOrCancelledByFundingId(FUNDING_ID)).thenReturn(Optional.empty());
        when(paymentRepository.findPendingByFundingId(FUNDING_ID)).thenReturn(Optional.of(pending));
        when(tossPaymentsClient.lookupByOrderId("fundit-order-1"))
                .thenThrow(new DependencyFailureException(new RuntimeException("timeout")));
        when(refundRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var result = refundExecutionService.executeFullRefund(FUNDING_ID, RefundTriggerType.SIMPLE_CHANGE_OF_MIND, "사유");

        // then — 토스에선 승인됐을 수 있어 PENDING으로 두고, 대사 배치가 다시 대조한다
        assertThat(result.status()).isEqualTo("PENDING_PAYMENT_UNRESOLVED");
        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentRepository, never()).failIfPending(any());
        verify(refundRequestRepository).save(any());
    }

    @Test
    void 대사_배치에서_한_건이_실패해도_나머지는_처리한다() {
        // given
        Instant before = Instant.now();
        Payment broken = completedPayment();
        Payment payment = completedPayment();
        RefundRequest first = RefundRequest.requestCancel(RefundTriggerType.SIMPLE_CHANGE_OF_MIND, FUNDING_ID,
                broken.getId(), 89_000L, "사유");
        RefundRequest second = RefundRequest.requestCancel(RefundTriggerType.SIMPLE_CHANGE_OF_MIND, FUNDING_ID,
                payment.getId(), 89_000L, "사유");
        when(refundRequestRepository.findCancelsRequestedBefore(before, 50)).thenReturn(List.of(first, second));
        when(paymentRepository.findById(broken.getId())).thenReturn(Optional.of(broken));
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(tossPaymentsClient.lookup("pay_key_1"))
                .thenThrow(new DependencyFailureException(new RuntimeException("timeout")))
                .thenReturn(new TossPaymentsClient.TossPaymentLookup("CANCELED",
                        new TossPaymentsClient.TossPaymentResult("pay_key_1", "fundit-order-1", null, "카드", null,
                                Instant.now(), 89_000L),
                        List.of(new TossPaymentsClient.TossCancelResult("tx_1", Instant.now(), 89_000L))));
        when(refundRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        int resolved = refundExecutionService.reconcileCancelsRequestedBefore(before, 50);

        // then
        assertThat(resolved).isEqualTo(1);
        assertThat(first.isCancelInFlight()).isTrue();
        assertThat(second.getStatus()).isEqualTo(RefundRequestStatus.COMPLETED);
    }
}
