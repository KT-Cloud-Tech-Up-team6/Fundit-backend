package com.fundit.payment.presentation.dto;

import com.fundit.payment.domain.refund.DefectType;
import com.fundit.payment.domain.refund.RefundReasonTag;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** PAYMENT-006 v2 요청 — fundingId는 order-service publicId(UUID). */
public record DefectRefundRequestV2(
        @NotNull UUID fundingId,
        @NotNull DefectType defectType,
        String reasonDetail,
        @NotEmpty List<String> evidenceUrls) {

    public String toReasonDetail() {
        return RefundReasonTag.format(defectType, reasonDetail);
    }
}
