package com.fundit.payment.domain.refund;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefundRequestUnitTest {

    private static final Long FUNDING_ID = 1024L;
    private static final UUID PAYMENT_ID = UUID.randomUUID();

    @Test
    void 하자환불을_신청하면_REQUESTED_상태로_생성된다() {
        // when
        RefundRequest request = RefundRequest.requestDefect(FUNDING_ID, PAYMENT_ID, "파손", List.of("https://cdn/a.jpg"));

        // then
        assertThat(request.getStatus()).isEqualTo(RefundRequestStatus.REQUESTED);
        assertThat(request.getTriggerType()).isEqualTo(RefundTriggerType.DEFECT);
    }

    @Test
    void 즉시처리_유형은_생성과_동시에_COMPLETED_상태다() {
        // when
        RefundRequest request = RefundRequest.completeImmediately(
                RefundTriggerType.SIMPLE_CHANGE_OF_MIND, FUNDING_ID, PAYMENT_ID, true);

        // then
        assertThat(request.getStatus()).isEqualTo(RefundRequestStatus.COMPLETED);
        assertThat(request.getIsFullRefund()).isTrue();
        assertThat(request.getProcessedAt()).isNotNull();
    }

    @Nested
    class 판매자_결정 {

        @Test
        void 승인하면_COMPLETED로_전환되고_전액여부가_기록된다() {
            // given
            RefundRequest request = RefundRequest.requestDefect(FUNDING_ID, PAYMENT_ID, "파손", List.of("url"));

            // when
            request.approve(false);

            // then
            assertThat(request.getStatus()).isEqualTo(RefundRequestStatus.COMPLETED);
            assertThat(request.getIsFullRefund()).isFalse();
        }

        @Test
        void 반려하면_REJECTED로_전환되고_사유가_기록된다() {
            // given
            RefundRequest request = RefundRequest.requestDefect(FUNDING_ID, PAYMENT_ID, "파손", List.of("url"));

            // when
            request.reject("제품 이상 없음 확인됨");

            // then
            assertThat(request.getStatus()).isEqualTo(RefundRequestStatus.REJECTED);
            assertThat(request.getRejectedReason()).isEqualTo("제품 이상 없음 확인됨");
        }
    }
}
