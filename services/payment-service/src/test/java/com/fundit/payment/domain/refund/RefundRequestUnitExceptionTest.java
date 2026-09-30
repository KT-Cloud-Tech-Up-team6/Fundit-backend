package com.fundit.payment.domain.refund;

import com.fundit.common.error.BusinessException;
import com.fundit.payment.domain.PaymentErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefundRequestUnitExceptionTest {

    private static final UUID FUNDING_ID = new UUID(0L, 1024L);
    private static final UUID PAYMENT_ID = UUID.randomUUID();

    @Test
    void 증빙자료가_없으면_하자환불_신청시_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> RefundRequest.requestAfterShipment(RefundTriggerType.DEFECT, FUNDING_ID, PAYMENT_ID, UUID.randomUUID(), "파손", List.of()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.EVIDENCE_REQUIRED));
    }

    @Test
    void 반려사유가_없으면_반려시_예외가_발생한다() {
        // given
        RefundRequest request = RefundRequest.requestAfterShipment(RefundTriggerType.DEFECT, FUNDING_ID, PAYMENT_ID, UUID.randomUUID(), "파손", List.of("url"));

        // when & then
        assertThatThrownBy(() -> request.reject(" "))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.REASON_REQUIRED));
    }

    @Test
    void 이미_처리된_신청은_다시_승인할_수_없다() {
        // given
        RefundRequest request = RefundRequest.requestAfterShipment(RefundTriggerType.DEFECT, FUNDING_ID, PAYMENT_ID, UUID.randomUUID(), "파손", List.of("url"));
        request.startCancel(50_000L, "하자환불 승인");

        // when & then
        assertThatThrownBy(() -> request.startCancel(50_000L, "하자환불 승인")).isInstanceOf(BusinessException.class);
    }

    @Test
    void DEFECT는_즉시처리로_생성할_수_없다() {
        // when & then
        assertThatThrownBy(() -> RefundRequest.requestCancel(RefundTriggerType.DEFECT, FUNDING_ID, PAYMENT_ID, 50_000L, "하자"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 대체계좌를_입력하면_값_객체로_보관한다() {
        // given
        RefundRequest request = RefundRequest.requestCancel(
                RefundTriggerType.GOAL_FAILED_AUTO, FUNDING_ID, PAYMENT_ID, 50_000L, "목표금액 미달 자동환불");
        request.awaitAlternateAccount();

        // when
        request.useAlternateAccount(new AlternateRefundAccount("국민", "홍길동", "123"));

        // then
        org.assertj.core.api.Assertions.assertThat(request.getAlternateRefundAccount().accountNumber()).isEqualTo("123");
        org.assertj.core.api.Assertions.assertThat(request.getStatus()).isEqualTo(RefundRequestStatus.REQUESTED);
    }

    @Test
    void 취소_요청_중이_아니면_확정할_수_없다() {
        // given
        RefundRequest request = RefundRequest.requestAfterShipment(RefundTriggerType.DEFECT, FUNDING_ID, PAYMENT_ID, UUID.randomUUID(), "파손", List.of("url"));

        // when & then
        assertThatThrownBy(() -> request.completeCancel(true)).isInstanceOf(BusinessException.class);
    }

    @Test
    void 대체계좌_대기_대상이_아닌_유형은_대체계좌_대기로_넘길_수_없다() {
        // given
        RefundRequest request = RefundRequest.requestCancel(
                RefundTriggerType.SIMPLE_CHANGE_OF_MIND, FUNDING_ID, PAYMENT_ID, 50_000L, "단순변심");

        // when & then
        assertThatThrownBy(request::awaitAlternateAccount).isInstanceOf(IllegalStateException.class);
    }
}
