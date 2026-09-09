package com.fundit.payment.presentation.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * PAYMENT-006 요청. {@code defectType}(불량/파손/표시광고상이)은 refund.refund_requests에
 * 별도 컬럼이 없어(PaymentERD.md 3장) reasonDetail 앞에 태그로 합쳐 저장한다.
 */
public record DefectRefundRequest(
        @NotNull Long fundingId,
        @NotNull DefectType defectType,
        String reasonDetail,
        @NotEmpty List<String> evidenceUrls) {

    public String toReasonDetail() {
        String detail = reasonDetail == null ? "" : reasonDetail;
        return "[" + defectType + "] " + detail;
    }

    public enum DefectType {
        DEFECTIVE, DAMAGED, DIFFERENT_FROM_DESCRIPTION
    }
}
