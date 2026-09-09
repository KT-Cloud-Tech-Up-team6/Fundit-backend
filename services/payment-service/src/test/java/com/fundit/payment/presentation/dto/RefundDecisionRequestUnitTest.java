package com.fundit.payment.presentation.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefundDecisionRequestUnitTest {

    @Test
    void APPROVED면_isApproved가_참이다() {
        assertThat(new RefundDecisionRequest(RefundDecisionRequest.Decision.APPROVED, null).isApproved()).isTrue();
        assertThat(new RefundDecisionRequest(RefundDecisionRequest.Decision.REJECTED, "사유").isApproved()).isFalse();
    }
}
