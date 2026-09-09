package com.fundit.payment.application.payment;

import com.fundit.common.error.BusinessException;
import com.fundit.payment.domain.PaymentErrorCode;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentMethod;
import com.fundit.payment.domain.payment.PaymentRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TossWebhookServiceUnitExceptionTest {

    @Mock
    private PaymentRepository paymentRepository;

    private TossWebhookService tossWebhookService;

    @BeforeEach
    void setUp() {
        tossWebhookService = new TossWebhookService(paymentRepository);
    }

    @Test
    void paymentKey나_secret이_없으면_서명_검증_실패다() {
        assertThatThrownBy(() -> tossWebhookService.handle("PAYMENT_STATUS_CHANGED", null, "DONE", "secret"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.WEBHOOK_SIGNATURE_INVALID));

        assertThatThrownBy(() -> tossWebhookService.handle("PAYMENT_STATUS_CHANGED", "pay_key", "DONE", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.WEBHOOK_SIGNATURE_INVALID));
    }

    @Test
    void 저장된_secret과_다르면_서명_검증_실패다() {
        // given
        Payment payment = Payment.create(1L, UUID.randomUUID(), "fundit-1", 10_000L, "주문", null, "idem");
        payment.markCompleted("pay_key_1", "real-secret", PaymentMethod.CARD, null, Instant.now());
        when(paymentRepository.findByPgPaymentKey("pay_key_1")).thenReturn(Optional.of(payment));

        // when & then
        assertThatThrownBy(() -> tossWebhookService.handle("PAYMENT_STATUS_CHANGED", "pay_key_1", "DONE", "forged"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.WEBHOOK_SIGNATURE_INVALID));
    }

    @Test
    void 아직_secret이_없는_결제는_서명_검증_실패다() {
        // given
        Payment payment = Payment.create(1L, UUID.randomUUID(), "fundit-1", 10_000L, "주문", null, "idem");
        when(paymentRepository.findByPgPaymentKey("pay_key_1")).thenReturn(Optional.of(payment));

        // when & then
        assertThatThrownBy(() -> tossWebhookService.handle("PAYMENT_STATUS_CHANGED", "pay_key_1", "DONE", "any"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.WEBHOOK_SIGNATURE_INVALID));
    }
}
