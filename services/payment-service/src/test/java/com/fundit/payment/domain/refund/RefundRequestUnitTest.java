package com.fundit.payment.domain.refund;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefundRequestUnitTest {

    private static final UUID FUNDING_ID = new UUID(0L, 1024L);
    private static final UUID PAYMENT_ID = UUID.randomUUID();

    @Test
    void 하자환불을_신청하면_REQUESTED_상태로_생성된다() {
        // when
        RefundRequest request = RefundRequest.requestAfterShipment(RefundTriggerType.DEFECT, FUNDING_ID, PAYMENT_ID, UUID.randomUUID(), "파손", List.of("https://cdn/a.jpg"));

        // then
        assertThat(request.getStatus()).isEqualTo(RefundRequestStatus.REQUESTED);
        assertThat(request.getTriggerType()).isEqualTo(RefundTriggerType.DEFECT);
    }

    @Nested
    class 취소_요청 {

        @Test
        void 즉시처리_유형은_취소_요청됨_상태로_생성된다() {
            // when
            RefundRequest request = RefundRequest.requestCancel(
                    RefundTriggerType.SIMPLE_CHANGE_OF_MIND, FUNDING_ID, PAYMENT_ID, 50_000L, "단순변심");

            // then
            assertThat(request.getStatus()).isEqualTo(RefundRequestStatus.PROCESSING);
            assertThat(request.isCancelInFlight()).isTrue();
            assertThat(request.getCancelAmount()).isEqualTo(50_000L);
            assertThat(request.getCancelReason()).isEqualTo("단순변심");
            assertThat(request.getReasonDetail()).isEqualTo("단순변심");
            assertThat(request.getCancelRequestedAt()).isNotNull();
        }

        @Test
        void 확정하면_COMPLETED가_되고_배치_대상에서_빠진다() {
            // given
            RefundRequest request = RefundRequest.requestCancel(
                    RefundTriggerType.SIMPLE_CHANGE_OF_MIND, FUNDING_ID, PAYMENT_ID, 50_000L, "단순변심");

            // when
            request.completeCancel(true);

            // then
            assertThat(request.getStatus()).isEqualTo(RefundRequestStatus.COMPLETED);
            assertThat(request.getIsFullRefund()).isTrue();
            assertThat(request.getProcessedAt()).isNotNull();
            assertThat(request.isCancelInFlight()).isFalse();
            assertThat(request.getCancelRequestedAt()).isNull();
        }

        @Test
        void 거절되면_대체계좌_대기로_REQUESTED가_되고_취소_요청이_비워진다() {
            // given
            RefundRequest request = RefundRequest.requestCancel(
                    RefundTriggerType.GOAL_FAILED_AUTO, FUNDING_ID, PAYMENT_ID, 50_000L, "목표금액 미달 자동환불");

            // when
            request.awaitAlternateAccount();

            // then
            assertThat(request.getStatus()).isEqualTo(RefundRequestStatus.REQUESTED);
            assertThat(request.isCancelInFlight()).isFalse();
            assertThat(request.getCancelAmount()).isNull();
        }
    }

    @Nested
    class 판매자_결정 {

        @Test
        void 승인하면_취소_요청됨으로_전환되고_확정시_전액여부가_기록된다() {
            // given
            RefundRequest request = RefundRequest.requestAfterShipment(RefundTriggerType.RETURN_CHANGE_OF_MIND, FUNDING_ID, PAYMENT_ID, UUID.randomUUID(), "변심", List.of());

            // when
            request.startCancel(45_000L, "반품 승인(반품비 차감)");

            // then
            assertThat(request.getStatus()).isEqualTo(RefundRequestStatus.PROCESSING);
            assertThat(request.getCancelAmount()).isEqualTo(45_000L);

            // when
            request.completeCancel(false);

            // then
            assertThat(request.getStatus()).isEqualTo(RefundRequestStatus.COMPLETED);
            assertThat(request.getIsFullRefund()).isFalse();
        }

        @Test
        void 승인_후_취소가_거절되면_다시_결정할_수_있게_REQUESTED로_되돌린다() {
            // given
            RefundRequest request = RefundRequest.requestAfterShipment(RefundTriggerType.DEFECT, FUNDING_ID, PAYMENT_ID, UUID.randomUUID(), "파손", List.of("url"));
            request.startCancel(50_000L, "하자환불 승인");

            // when
            request.revertCancel();

            // then
            assertThat(request.getStatus()).isEqualTo(RefundRequestStatus.REQUESTED);
            assertThat(request.isCancelInFlight()).isFalse();
        }

        @Test
        void 반려하면_REJECTED로_전환되고_사유가_기록된다() {
            // given
            RefundRequest request = RefundRequest.requestAfterShipment(RefundTriggerType.DEFECT, FUNDING_ID, PAYMENT_ID, UUID.randomUUID(), "파손", List.of("url"));

            // when
            request.reject("제품 이상 없음 확인됨");

            // then
            assertThat(request.getStatus()).isEqualTo(RefundRequestStatus.REJECTED);
            assertThat(request.getRejectedReason()).isEqualTo("제품 이상 없음 확인됨");
        }
    }
}
