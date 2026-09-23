package com.fundit.payment.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * 교환 신청 요청 — fundingId는 order-service publicId(UUID). 교환 전용 사유 5종의 정확한
 * 라벨이 아직 백엔드에 확정되지 않아(디자인 확인 필요) {@code reasonDetail}은 자유 입력 문자열로
 * 받는다 — 고정 enum이 필요하면 정확한 라벨 목록을 받아 {@code DefectRefundRequest.DefectType}처럼
 * enum으로 전환한다.
 */
public record ExchangeRequestV2(
        @NotNull UUID fundingId,
        @NotBlank String reasonDetail,
        @NotEmpty List<@NotBlank String> evidenceUrls) {
}
