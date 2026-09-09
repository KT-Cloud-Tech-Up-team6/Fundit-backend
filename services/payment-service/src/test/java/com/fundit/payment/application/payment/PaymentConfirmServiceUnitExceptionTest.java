package com.fundit.payment.application.payment;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.application.event.PaymentEventPublisher;
import com.fundit.payment.application.settlement.SettlementHoldService;
import com.fundit.payment.domain.PaymentErrorCode;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.payment.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmServiceUnitExceptionTest {

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final Long FUNDING_ID = 1024L;

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private TossPaymentsClient tossPaymentsClient;
    @Mock
    private PaymentEventPublisher paymentEventPublisher;
    @Mock
    private SettlementHoldService settlementHoldService;

    private PaymentConfirmService paymentConfirmService;

    @BeforeEach
    void setUp() {
        paymentConfirmService = new PaymentConfirmService(paymentRepository, tossPaymentsClient,
                paymentEventPublisher, settlementHoldService);
    }

    @Test
    void 금액이_스냅샷과_다르면_토스_호출_없이_예외가_발생한다() {
        // given
        Payment payment = Payment.create(FUNDING_ID, MEMBER_ID, "fundit-order-1", 89_000L, "테스트 주문", null, "idem");
        when(paymentRepository.findByPgOrderId("fundit-order-1")).thenReturn(Optional.of(payment));

        // when & then
        assertThatThrownBy(() -> paymentConfirmService.confirm(MEMBER_ID, "pay_key_1", "fundit-order-1", 1_000L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH));
        org.mockito.Mockito.verifyNoInteractions(tossPaymentsClient);
    }

    @Test
    void 본인_결제건이_아니면_FORBIDDEN_예외가_발생한다() {
        // given
        Payment payment = Payment.create(FUNDING_ID, UUID.randomUUID(), "fundit-order-1", 89_000L, "테스트 주문", null, "idem");
        when(paymentRepository.findByPgOrderId("fundit-order-1")).thenReturn(Optional.of(payment));

        // when & then
        assertThatThrownBy(() -> paymentConfirmService.confirm(MEMBER_ID, "pay_key_1", "fundit-order-1", 89_000L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    @Test
    void 토스_세션만료_응답이면_결제가_FAILED_처리되고_PAYMENT_EXPIRED_예외가_발생한다() {
        // given
        Payment payment = Payment.create(FUNDING_ID, MEMBER_ID, "fundit-order-1", 89_000L, "테스트 주문", null, "idem");
        when(paymentRepository.findByPgOrderId("fundit-order-1")).thenReturn(Optional.of(payment));
        when(tossPaymentsClient.confirm("pay_key_1", "fundit-order-1", 89_000L))
                .thenThrow(new TossApiException(TossApiException.NOT_FOUND_PAYMENT_SESSION, "만료"));
        when(paymentRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        // when & then
        assertThatThrownBy(() -> paymentConfirmService.confirm(MEMBER_ID, "pay_key_1", "fundit-order-1", 89_000L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.PAYMENT_EXPIRED));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void 토스_승인_실패면_결제가_FAILED_처리되고_PG_CONFIRM_FAILED_예외가_발생한다() {
        // given
        Payment payment = Payment.create(FUNDING_ID, MEMBER_ID, "fundit-order-1", 89_000L, "테스트 주문", null, "idem");
        when(paymentRepository.findByPgOrderId("fundit-order-1")).thenReturn(Optional.of(payment));
        when(tossPaymentsClient.confirm("pay_key_1", "fundit-order-1", 89_000L))
                .thenThrow(new TossApiException("EXCEED_MAX_AUTH_COUNT", "인증 횟수 초과"));
        when(paymentRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        // when & then
        assertThatThrownBy(() -> paymentConfirmService.confirm(MEMBER_ID, "pay_key_1", "fundit-order-1", 89_000L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.PG_CONFIRM_FAILED));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }
}
