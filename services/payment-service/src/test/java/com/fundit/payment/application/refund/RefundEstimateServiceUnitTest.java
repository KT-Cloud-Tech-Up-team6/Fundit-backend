package com.fundit.payment.application.refund;

import com.fundit.payment.application.funding.OrderFundingClient;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundEstimateServiceUnitTest {

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = new UUID(0L, 1024L);

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderFundingClient orderFundingClient;

    private RefundEstimateService refundEstimateService;

    @BeforeEach
    void setUp() {
        refundEstimateService = new RefundEstimateService(paymentRepository, orderFundingClient);
    }

    @Test
    void 본인_완료결제이면_배송비와_할인을_분리한_전액_환불_예상액을_반환한다() {
        // given
        Payment payment = Payment.create(ORDER_ID, MEMBER_ID, "fundit-1", 90_000L, "주문", null, "idem");
        payment.markCompleted("pay_key", "secret", PaymentMethod.CARD, null, Instant.now());
        when(paymentRepository.findCompletedByFundingId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(orderFundingClient.fetch(ORDER_ID)).thenReturn(
                new OrderFundingClient.FundingSnapshot(MEMBER_ID, UUID.randomUUID(), "GOAL_ACHIEVED", 90_000L, "주문",
                        null, ORDER_ID, 3_000L, 2_000L));

        // when
        RefundEstimateService.RefundEstimate estimate = refundEstimateService.estimate(MEMBER_ID, ORDER_ID);

        // then
        assertThat(estimate.orderId()).isEqualTo(ORDER_ID);
        assertThat(estimate.rewardAmount()).isEqualTo(89_000L);
        assertThat(estimate.shippingFee()).isEqualTo(3_000L);
        assertThat(estimate.discountAmount()).isEqualTo(2_000L);
        assertThat(estimate.refundAmount()).isEqualTo(90_000L);
    }
}
