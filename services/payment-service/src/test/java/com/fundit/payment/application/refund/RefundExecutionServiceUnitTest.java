package com.fundit.payment.application.refund;

import com.fundit.payment.application.event.PaymentEventPublisher;
import com.fundit.payment.application.payment.TossPaymentsClient;
import com.fundit.payment.application.settlement.SettlementHoldService;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentMethod;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.payment.PaymentStatus;
import com.fundit.payment.domain.refund.RefundRequest;
import com.fundit.payment.domain.refund.RefundRequestRepository;
import com.fundit.payment.domain.refund.RefundTriggerType;
import com.fundit.payment.infrastructure.persistence.payment.PaymentCancellationJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundExecutionServiceUnitTest {

    private static final Long FUNDING_ID = 1024L;
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
    private SettlementHoldService settlementHoldService;

    private RefundExecutionService refundExecutionService;

    @BeforeEach
    void setUp() {
        refundExecutionService = new RefundExecutionService(paymentRepository, tossPaymentsClient,
                paymentCancellationJpaRepository, refundRequestRepository, paymentEventPublisher,
                settlementHoldService);
    }

    private Payment completedPayment() {
        Payment payment = Payment.create(FUNDING_ID, MEMBER_ID, "fundit-order-1", 89_000L, "테스트 주문", 7L, "idem");
        payment.markCompleted("pay_key_1", "secret_1", PaymentMethod.CARD, null, Instant.now());
        return payment;
    }

    @Test
    void 전액취소에_성공하면_결제가_CANCELLED로_전환되고_보류금이_해제된다() {
        // given
        Payment payment = completedPayment();
        when(paymentRepository.findCompletedOrCancelledByFundingId(FUNDING_ID)).thenReturn(Optional.of(payment));
        when(tossPaymentsClient.cancel("pay_key_1", 89_000L, "구매자 단순변심 참여 취소"))
                .thenReturn(new TossPaymentsClient.TossCancelResult("tx_1", Instant.now(), 89_000L));
        when(refundRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var result = refundExecutionService.executeFullRefund(FUNDING_ID, RefundTriggerType.SIMPLE_CHANGE_OF_MIND,
                "구매자 단순변심 참여 취소");

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(result.fullRefund()).isTrue();
        verify(settlementHoldService).releaseToRefund(payment.getId());
        verify(paymentEventPublisher).publishRefundCompleted(any());
        verify(paymentCancellationJpaRepository).save(any());
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
        RefundRequest refundRequest = RefundRequest.requestDefect(FUNDING_ID, payment.getId(), "파손", java.util.List.of("url"));
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(tossPaymentsClient.cancel("pay_key_1", 50_000L, "하자환불 승인"))
                .thenReturn(new TossPaymentsClient.TossCancelResult("tx_2", Instant.now(), 50_000L));
        when(refundRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var result = refundExecutionService.executeApprovedRefund(refundRequest, 50_000L, "하자환불 승인");

        // then
        assertThat(result.fullRefund()).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED); // 부분취소라 CANCELLED로 바뀌지 않음
        verify(settlementHoldService, never()).releaseToRefund(any());
    }

    @Test
    void 토스_취소_실패시_대체계좌_대기_상태로_전환한다() {
        // given
        Payment payment = completedPayment();
        when(paymentRepository.findCompletedOrCancelledByFundingId(FUNDING_ID)).thenReturn(Optional.of(payment));
        when(tossPaymentsClient.cancel(any(), any(Long.class), any()))
                .thenThrow(new com.fundit.payment.application.payment.TossApiException("REJECT_CARD_COMPANY", "카드사 거절"));
        ArgumentCaptor<RefundRequest> captor = ArgumentCaptor.forClass(RefundRequest.class);

        // when
        var result = refundExecutionService.executeFullRefundOrAwaitAlternateAccount(FUNDING_ID,
                RefundTriggerType.GOAL_FAILED_AUTO, "목표 미달 자동환불");

        // then
        assertThat(result.status()).isEqualTo("AWAITING_ALTERNATE_ACCOUNT");
        verify(refundRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getTriggerType()).isEqualTo(RefundTriggerType.GOAL_FAILED_AUTO);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED); // 취소되지 않고 그대로 유지
    }
}
