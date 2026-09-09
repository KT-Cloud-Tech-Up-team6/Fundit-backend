package com.fundit.payment.application.refund;

import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentMethod;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.refund.RefundTriggerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShippingDelayRefundServiceUnitTest {

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final Long FUNDING_ID = 1024L;

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ShippingStatusClient shippingStatusClient;
    @Mock
    private RefundExecutionService refundExecutionService;

    private ShippingDelayRefundService shippingDelayRefundService;

    @BeforeEach
    void setUp() {
        shippingDelayRefundService = new ShippingDelayRefundService(paymentRepository, shippingStatusClient,
                refundExecutionService);
    }

    @Test
    void 미발송이면_즉시_전액취소를_실행한다() {
        // given
        Payment payment = Payment.create(FUNDING_ID, MEMBER_ID, "fundit-1", 89_000L, "주문", null, "idem");
        payment.markCompleted("pay_key", "secret", PaymentMethod.CARD, null, Instant.now());
        when(paymentRepository.findCompletedByFundingId(FUNDING_ID)).thenReturn(Optional.of(payment));
        when(shippingStatusClient.isAlreadyShipped(FUNDING_ID)).thenReturn(false);
        when(refundExecutionService.executeFullRefundOrAwaitAlternateAccount(FUNDING_ID,
                RefundTriggerType.SHIPPING_DELAY, "발송지연 결제취소"))
                .thenReturn(new RefundExecutionService.RefundExecutionResult(9L, "COMPLETED", true));

        // when
        var result = shippingDelayRefundService.requestCancel(MEMBER_ID, FUNDING_ID);

        // then
        assertThat(result.refundId()).isEqualTo(9L);
        assertThat(result.status()).isEqualTo("COMPLETED");
        verify(refundExecutionService).executeFullRefundOrAwaitAlternateAccount(FUNDING_ID,
                RefundTriggerType.SHIPPING_DELAY, "발송지연 결제취소");
    }
}
