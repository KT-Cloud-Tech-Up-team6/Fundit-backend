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
    }
}
