package com.fundit.payment.presentation.dto;

import com.fundit.payment.domain.refund.RefundReasonTag;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * 발송 후(수령 후) 구매자 귀책 반품 신청 — 환불 정책 V.1.0 「취소·반품·교환 공통 정책」.
 * 증빙({@code evidenceUrls})은 구매자 귀책이라 선택이다(하자환불과 다른 점).
 */
public record ReturnRequestV2(
        @NotNull UUID fundingId,
        @NotNull ReturnReason returnReason,
        @Size(max = 500) String reasonDetail,
        List<String> evidenceUrls) {

    /** 반품 사유 유형. 둘 다 구매자 귀책이라 반품 배송비 부담 규칙이 같다. */
    public enum ReturnReason {
        CHANGE_OF_MIND, WRONG_OPTION
    }

    /** DEFECT와 동일하게 사유 유형을 태그로 앞에 붙여 저장한다(별도 컬럼 없음). */
    public String toReasonDetail() {
        return RefundReasonTag.format(returnReason, reasonDetail);
    }
}
