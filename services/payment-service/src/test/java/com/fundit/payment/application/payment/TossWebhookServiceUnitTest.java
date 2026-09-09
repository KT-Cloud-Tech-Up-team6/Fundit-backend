package com.fundit.payment.application.payment;

import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TossWebhookServiceUnitTest {

    @Mock
    private PaymentRepository paymentRepository;

    private TossWebhookService tossWebhookService;

    @BeforeEach
    void setUp() {
        tossWebhookService = new TossWebhookService(paymentRepository);
    }

    @Test
    void 서명이_일치하면_예외_없이_처리한다() {
        // given
        Payment payment = Payment.create(1L, UUID.randomUUID(), "fundit-1", 10_000L, "주문", null, "idem");
        payment.markCompleted("pay_key_1", "webhook-secret", com.fundit.payment.domain.payment.PaymentMethod.CARD,
                null, java.time.Instant.now());
        when(paymentRepository.findByPgPaymentKey("pay_key_1")).thenReturn(Optional.of(payment));

        // when & then — 예외가 발생하지 않아야 한다
        tossWebhookService.handle("PAYMENT_STATUS_CHANGED", "pay_key_1", "DONE", "webhook-secret");
        verify(paymentRepository).findByPgPaymentKey("pay_key_1");
    }

    @Test
    void 모르는_paymentKey면_조용히_무시한다() {
        // given
        when(paymentRepository.findByPgPaymentKey("unknown")).thenReturn(Optional.empty());

        // when & then — 존재 여부를 노출하지 않기 위해 예외를 던지지 않는다
        tossWebhookService.handle("PAYMENT_STATUS_CHANGED", "unknown", "DONE", "secret");
    }
}
