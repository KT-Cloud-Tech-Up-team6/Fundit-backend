package com.fundit.payment.domain.refund;

import com.fundit.common.error.BusinessException;
import com.fundit.payment.domain.PaymentErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefundRequestUnitExceptionTest {

    private static final Long FUNDING_ID = 1024L;
    private static final UUID PAYMENT_ID = UUID.randomUUID();

    @Test
    void 증빙자료가_없으면_하자환불_신청시_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> RefundRequest.requestDefect(FUNDING_ID, PAYMENT_ID, "파손", List.of()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.EVIDENCE_REQUIRED));
    }

    @Test
    void 반려사유가_없으면_반려시_예외가_발생한다() {
        // given
        RefundRequest request = RefundRequest.requestDefect(FUNDING_ID, PAYMENT_ID, "파손", List.of("url"));

        // when & then
        assertThatThrownBy(() -> request.reject(" "))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.REASON_REQUIRED));
    }

    @Test
    void 이미_처리된_신청은_다시_승인할_수_없다() {
        // given
        RefundRequest request = RefundRequest.requestDefect(FUNDING_ID, PAYMENT_ID, "파손", List.of("url"));
        request.approve(true);

        // when & then
        assertThatThrownBy(() -> request.approve(true)).isInstanceOf(BusinessException.class);
    }

    @Test
    void DEFECT는_즉시처리로_생성할_수_없다() {
        // when & then
        assertThatThrownBy(() -> RefundRequest.completeImmediately(RefundTriggerType.DEFECT, FUNDING_ID, PAYMENT_ID, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
