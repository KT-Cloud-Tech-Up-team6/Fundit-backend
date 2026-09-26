package com.fundit.payment.application.payment;

import com.fundit.payment.application.event.PaymentEventPublisher;
import com.fundit.payment.application.settlement.SettlementHoldService;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.payment.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmServiceUnitTest {

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID FUNDING_ID = new UUID(0L, 1024L);

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private TossPaymentsClient tossPaymentsClient;
    @Mock
    private PaymentEventPublisher paymentEventPublisher;
    @Mock
    private SettlementHoldService settlementHoldService;
    @Mock
    private PaymentFailureRecorder paymentFailureRecorder;
    @Mock
    private ExchangeFeePaymentListener exchangeFeePaymentListener;

    private PaymentConfirmService paymentConfirmService;

    @BeforeEach
    void setUp() {
        paymentConfirmService = new PaymentConfirmService(paymentRepository, tossPaymentsClient,
                paymentEventPublisher, settlementHoldService, paymentFailureRecorder,
                exchangeFeePaymentListener);
    }

    @Test
    void 승인에_성공하면_결제가_완료되고_이벤트가_발행된다() {
        // given
        Payment payment = Payment.create(FUNDING_ID, MEMBER_ID, "fundit-order-1", 89_000L, "테스트 주문", List.of(7L), "idem");
        when(paymentRepository.findByPgOrderId("fundit-order-1")).thenReturn(Optional.of(payment));
        when(tossPaymentsClient.confirm("pay_key_1", "fundit-order-1", 89_000L)).thenReturn(
                new TossPaymentsClient.TossPaymentResult("pay_key_1", "fundit-order-1", "secret_1", "간편결제",
                        "KAKAOPAY", Instant.now(), 89_000L));
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        var result = paymentConfirmService.confirm(MEMBER_ID, "pay_key_1", "fundit-order-1", 89_000L);

        // then
        assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED.name());
        verify(settlementHoldService).openHold(payment.getId(), null, 89_000L);
        verify(paymentEventPublisher).publishPaymentCompleted(any());
    }

    @Test
    void 같은_paymentKey로_재시도하면_토스를_다시_호출하지_않고_기존_결과를_반환한다() {
        // given
        Payment payment = Payment.create(FUNDING_ID, MEMBER_ID, "fundit-order-1", 89_000L, "테스트 주문", null, "idem");
        payment.markCompleted("pay_key_1", "secret_1", com.fundit.payment.domain.payment.PaymentMethod.CARD, null,
                Instant.now());
        when(paymentRepository.findByPgOrderId("fundit-order-1")).thenReturn(Optional.of(payment));

        // when
        var result = paymentConfirmService.confirm(MEMBER_ID, "pay_key_1", "fundit-order-1", 89_000L);

        // then
        assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED.name());
        org.mockito.Mockito.verifyNoInteractions(tossPaymentsClient);
    }

    /** 교환 배송비는 주문 결제가 아니다 — order-service 통지(PaymentCompleted)도, 정산 보류도 없다. */
    @Test
    void 교환_배송비_결제는_교환_흐름으로만_넘어간다() {
        // given
        Payment feePayment = Payment.createExchangeFee(FUNDING_ID, MEMBER_ID, "fundit-fee-1", 5_000L, "교환 배송비",
                77L, "idem-fee");
        when(paymentRepository.findByPgOrderId("fundit-fee-1")).thenReturn(Optional.of(feePayment));
        when(tossPaymentsClient.confirm("pay_key_fee", "fundit-fee-1", 5_000L)).thenReturn(
                new TossPaymentsClient.TossPaymentResult("pay_key_fee", "fundit-fee-1", "secret_fee", "카드",
                        null, Instant.now(), 5_000L));
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        var result = paymentConfirmService.confirm(MEMBER_ID, "pay_key_fee", "fundit-fee-1", 5_000L);

        // then
        assertThat(result.status()).isEqualTo("COMPLETED");
        verify(exchangeFeePaymentListener).onExchangeFeePaid(any());
        verifyNoInteractions(paymentEventPublisher, settlementHoldService);
    }
}
