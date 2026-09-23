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
class SimpleChangeOfMindRefundServiceUnitTest {

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID FUNDING_ID = new UUID(0L, 1024L);

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ShippingStatusClient shippingStatusClient;
    @Mock
    private RefundExecutionService refundExecutionService;

    private SimpleChangeOfMindRefundService simpleChangeOfMindRefundService;

    @BeforeEach
    void setUp() {
        simpleChangeOfMindRefundService = new SimpleChangeOfMindRefundService(paymentRepository,
                shippingStatusClient, refundExecutionService);
    }

    @Test
    void 발송_전이면_즉시_전액취소를_실행한다() {
        // given
        Payment payment = Payment.create(FUNDING_ID, MEMBER_ID, "fundit-1", 89_000L, "주문", null, "idem");
        payment.markCompleted("pay_key", "secret", PaymentMethod.CARD, null, Instant.now());
        when(paymentRepository.findCompletedByFundingId(FUNDING_ID)).thenReturn(Optional.of(payment));
        when(shippingStatusClient.fetch(FUNDING_ID))
                .thenReturn(new ShippingStatusClient.ShippingStatus(false, false, null, null));
        when(refundExecutionService.executeFullRefundOrAwaitAlternateAccount(FUNDING_ID,
                RefundTriggerType.SIMPLE_CHANGE_OF_MIND, "단순변심 환불신청"))
                .thenReturn(new RefundExecutionService.RefundExecutionResult(9L, "COMPLETED", true));

        // when
        var result = simpleChangeOfMindRefundService.requestCancel(MEMBER_ID, FUNDING_ID);

        // then
        assertThat(result.refundId()).isEqualTo(9L);
        assertThat(result.status()).isEqualTo("COMPLETED");
        verify(refundExecutionService).executeFullRefundOrAwaitAlternateAccount(FUNDING_ID,
                RefundTriggerType.SIMPLE_CHANGE_OF_MIND, "단순변심 환불신청");
    }
}
