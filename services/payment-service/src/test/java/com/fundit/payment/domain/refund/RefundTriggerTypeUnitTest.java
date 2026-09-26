package com.fundit.payment.domain.refund;

import com.fundit.payment.application.event.PaymentEventPublisher.RefundReason;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefundTriggerTypeUnitTest {

    @Test
    void order_service가_기대하는_RefundReason으로_매핑한다() {
        assertThat(RefundTriggerType.GOAL_FAILED_AUTO.toOrderServiceReason())
                .isEqualTo(RefundReason.GOAL_FAILURE_AUTO_REFUND);
        assertThat(RefundTriggerType.SYSTEM_RECONCILIATION.toOrderServiceReason())
                .isEqualTo(RefundReason.GOAL_FAILURE_AUTO_REFUND);
        assertThat(RefundTriggerType.SIMPLE_CHANGE_OF_MIND.toOrderServiceReason())
                .isEqualTo(RefundReason.CANCELLED_BY_MEMBER);
        assertThat(RefundTriggerType.DEFECT.toOrderServiceReason())
                .isEqualTo(RefundReason.POST_SUCCESS_DEFECT);
        assertThat(RefundTriggerType.SHIPPING_DELAY.toOrderServiceReason())
                .isEqualTo(RefundReason.POST_SUCCESS_DELAY);
        assertThat(RefundTriggerType.RETURN_CHANGE_OF_MIND.toOrderServiceReason())
                .isEqualTo(RefundReason.POST_SUCCESS_RETURN);
    }

    @Test
    void 판매자_검토_대상은_하자환불과_구매자_귀책_반품뿐이다() {
        assertThat(RefundTriggerType.DEFECT.isSellerDecisionTarget()).isTrue();
        assertThat(RefundTriggerType.RETURN_CHANGE_OF_MIND.isSellerDecisionTarget()).isTrue();
        assertThat(RefundTriggerType.EXCHANGE.isSellerDecisionTarget()).isFalse();
        assertThat(RefundTriggerType.SHIPPING_DELAY.isSellerDecisionTarget()).isFalse();
    }

    @Test
    void 발송_후_신청_유형은_기한_검사_대상_세_가지다() {
        assertThat(RefundTriggerType.postShipmentTypes())
                .containsExactlyInAnyOrder(RefundTriggerType.DEFECT, RefundTriggerType.EXCHANGE,
                        RefundTriggerType.RETURN_CHANGE_OF_MIND);
    }
}
