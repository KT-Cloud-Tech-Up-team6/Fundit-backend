package com.fundit.payment.presentation.dto;

import com.fundit.payment.domain.refund.ExchangeReason;
import com.fundit.payment.domain.refund.RefundReasonTag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * 교환 신청 요청 — fundingId는 order-service publicId(UUID). {@code exchangeReason}이 교환
 * 배송비 부담 주체를 정한다(구매자 귀책이면 5,000원 별도 결제, 환불 정책 V.1.0).
 *
 * <p>{@code exchangeReason}은 아직 선택이다 — FE가 사유를 보내도록 전환하는 동안 기존 요청
 * (사유 없이 {@code reasonDetail} 문자열만)도 계속 접수돼야 해서, 미전송은 {@code OTHER}
 * (기타·귀책 불분명)로 정규화한다. FE 전환이 끝나면 {@code @NotNull}로 바꾼다.
 *
 * <p>{@code evidenceUrls}는 선택이다 — 환불 정책 V.1.0이 단순변심·옵션 선택 오류 교환도 범위에
 * 넣었고, 구매자 귀책 사유에는 증빙을 요구하지 않는다(판매자 귀책 교환이면 증빙을 붙이는 편이
 * 검토가 빠르다는 안내는 FE 문구로 처리).
 */
public record ExchangeRequestV2(
        @NotNull UUID fundingId,
        ExchangeReason exchangeReason,
        @Size(max = 500) String reasonDetail,
        List<@NotBlank String> evidenceUrls) {

    public ExchangeRequestV2 {
        exchangeReason = exchangeReason == null ? ExchangeReason.OTHER : exchangeReason;
    }

    /** 하자환불·반품과 동일하게 사유 유형을 태그로 앞에 붙여 저장한다(별도 컬럼 없음). */
    public String toReasonDetail() {
        return RefundReasonTag.format(exchangeReason, reasonDetail);
    }
}
