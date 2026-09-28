package com.fundit.payment.application.refund;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.application.event.PaymentEventPublisher;
import com.fundit.payment.application.notification.PaymentNotificationPublisher;
import com.fundit.payment.application.payment.TossApiException;
import com.fundit.payment.application.payment.TossPaymentsClient;
import com.fundit.payment.application.settlement.SettlementHoldService;
import com.fundit.payment.domain.PaymentErrorCode;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentMethod;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.refund.RefundRequestRepository;
import com.fundit.payment.domain.refund.RefundTriggerType;
import com.fundit.payment.infrastructure.persistence.payment.PaymentCancellationJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
                paymentNotificationPublisher, settlementHoldService);
    }

    private Payment completedPayment() {
        Payment payment = Payment.create(FUNDING_ID, MEMBER_ID, "fundit-order-1", 89_000L, "테스트 주문", null, "idem");
        payment.markCompleted("pay_key_1", "secret_1", PaymentMethod.CARD, null, Instant.now());
        return payment;
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
    void 토스_취소_실패시_PG_CANCEL_FAILED_예외가_발생하고_결제상태는_그대로다() {
        // given
        Payment payment = completedPayment();
        when(paymentRepository.findCompletedOrCancelledByFundingId(FUNDING_ID)).thenReturn(Optional.of(payment));
        when(tossPaymentsClient.cancel(any(), any(Long.class), any()))
                .thenThrow(new TossApiException("ALREADY_CANCELED_PAYMENT", "이미 취소됨"));

        // when & then
        assertThatThrownBy(() -> refundExecutionService.executeFullRefund(FUNDING_ID,
                RefundTriggerType.SIMPLE_CHANGE_OF_MIND, "사유"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.PG_CANCEL_FAILED));

        // then — 취소 실행 기록/이벤트 발행이 전혀 일어나지 않아야 한다
        verifyNoInteractions(paymentCancellationJpaRepository);
        verifyNoInteractions(paymentEventPublisher);
        verifyNoInteractions(settlementHoldService);
    }

    @Test
    void 승인_대상_결제를_찾을_수_없으면_NOT_FOUND_예외가_발생한다() {
        // given
        var refundRequest = com.fundit.payment.domain.refund.RefundRequest.requestAfterShipment(com.fundit.payment.domain.refund.RefundTriggerType.DEFECT, 
                FUNDING_ID, UUID.randomUUID(), UUID.randomUUID(), "파손", java.util.List.of("url"));
        when(paymentRepository.findById(refundRequest.getPaymentId())).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> refundExecutionService.executeApprovedRefund(refundRequest, 10_000L, "사유"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    void 대기_결제의_토스_조회가_실패하면_FAILED로_닫지_않고_예외가_발생한다() {
        // given
        Payment pending = Payment.create(FUNDING_ID, MEMBER_ID, "fundit-order-1", 89_000L, "테스트 주문", null, "idem");
        when(paymentRepository.findCompletedOrCancelledByFundingId(FUNDING_ID)).thenReturn(Optional.empty());
        when(paymentRepository.findPendingByFundingId(FUNDING_ID)).thenReturn(Optional.of(pending));
        when(tossPaymentsClient.lookupByOrderId("fundit-order-1"))
                .thenThrow(new com.fundit.common.error.DependencyFailureException(new RuntimeException("timeout")));

        // when & then — 토스에선 승인됐을 수 있어 PENDING으로 둔다
        assertThatThrownBy(() -> refundExecutionService.executeFullRefund(FUNDING_ID,
                RefundTriggerType.SIMPLE_CHANGE_OF_MIND, "사유"))
                .isInstanceOf(com.fundit.common.error.DependencyFailureException.class);
        assertThat(pending.getStatus()).isEqualTo(com.fundit.payment.domain.payment.PaymentStatus.PENDING);
    }
}
