package com.fundit.payment.application.refund;

import com.fundit.payment.application.funding.OrderFundingClient;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentMethod;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.refund.DefectType;
import com.fundit.payment.domain.refund.ExchangeReason;
import com.fundit.payment.domain.refund.RefundTriggerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

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
        givenCompletedPayment(90_000L, 3_000L, 2_000L);

        // when
        RefundEstimateService.RefundEstimate estimate = refundEstimateService.estimate(MEMBER_ID, ORDER_ID, null,
                null, null);

        // then
        assertThat(estimate.orderId()).isEqualTo(ORDER_ID);
        assertThat(estimate.paymentAmount()).isEqualTo(90_000L);
        assertThat(estimate.rewardAmount()).isEqualTo(89_000L);
        assertThat(estimate.shippingFee()).isEqualTo(3_000L);
        assertThat(estimate.discountAmount()).isEqualTo(2_000L);
        assertThat(estimate.refundAmount()).isEqualTo(90_000L);
        assertThat(estimate.returnShippingFee()).isZero();
        assertThat(estimate.additionalPaymentAmount()).isZero();
        assertThat(estimate.confirmed()).isTrue();
    }

    /** 환불 정책 V.1.0 「취소·반품·교환 공통 정책」의 유형·사유별 금액 표(PaymentApiSpec.md 2-6). */
    @ParameterizedTest(name = "[{index}] {0}/{1}{2} → 환불 {3}, 반품비 {4}, 추가결제 {5}, 확정 {6}")
    @MethodSource("금액_표")
    void 유형과_사유에_따라_차감_가산_확정여부가_정해진다(RefundTriggerType triggerType, DefectType defectType,
                                        ExchangeReason exchangeReason, Long expectedRefundAmount,
                                        long expectedReturnShippingFee, long expectedAdditionalPaymentAmount,
                                        boolean expectedConfirmed) {
        // given — 결제 23,000원(배송비 3,000원 포함, 할인 없음)
        givenCompletedPayment(23_000L, 3_000L, 0L);

        // when
        RefundEstimateService.RefundEstimate estimate = refundEstimateService.estimate(MEMBER_ID, ORDER_ID,
                triggerType, defectType, exchangeReason);

        // then
        assertThat(estimate.refundAmount()).isEqualTo(expectedRefundAmount);
        assertThat(estimate.returnShippingFee()).isEqualTo(expectedReturnShippingFee);
        assertThat(estimate.additionalPaymentAmount()).isEqualTo(expectedAdditionalPaymentAmount);
        assertThat(estimate.confirmed()).isEqualTo(expectedConfirmed);
    }

    private static Stream<Arguments> 금액_표() {
        return Stream.of(
                Arguments.of(null, null, null, 23_000L, 0L, 0L, true),
                Arguments.of(RefundTriggerType.RETURN_CHANGE_OF_MIND, null, null, 18_000L, 5_000L, 0L, true),
                Arguments.of(RefundTriggerType.SHIPPING_DELAY, null, null, 23_000L, 0L, 0L, true),
                Arguments.of(RefundTriggerType.DEFECT, DefectType.DAMAGED, null, 23_000L, 0L, 0L, false),
                Arguments.of(RefundTriggerType.DEFECT, DefectType.OTHER, null, null, 0L, 0L, false),
                Arguments.of(RefundTriggerType.EXCHANGE, null, ExchangeReason.CHANGE_OF_MIND, null, 0L, 5_000L, true),
                Arguments.of(RefundTriggerType.EXCHANGE, null, ExchangeReason.WRONG_DELIVERY, null, 0L, 0L, false),
                Arguments.of(RefundTriggerType.EXCHANGE, null, null, null, 0L, 0L, false));
    }

    private void givenCompletedPayment(long amount, long shippingFee, long discountAmount) {
        Payment payment = Payment.create(ORDER_ID, MEMBER_ID, "fundit-1", amount, "주문", null, "idem");
        payment.markCompleted("pay_key", "secret", PaymentMethod.CARD, null, Instant.now());
        when(paymentRepository.findCompletedByFundingId(ORDER_ID)).thenReturn(Optional.of(payment));
        when(orderFundingClient.fetch(ORDER_ID)).thenReturn(
                new OrderFundingClient.FundingSnapshot(MEMBER_ID, UUID.randomUUID(), "GOAL_ACHIEVED", amount, "주문",
                        null, ORDER_ID, shippingFee, discountAmount));
    }
}
