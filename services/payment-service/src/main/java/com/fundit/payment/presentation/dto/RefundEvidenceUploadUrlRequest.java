package com.fundit.payment.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/** F09 — 구매자 증빙 업로드 주소 발급 요청. {@code orderId}는 order-service의 orderId(UUID)다. */
public record RefundEvidenceUploadUrlRequest(
        @NotNull UUID orderId,
        @NotBlank String fileName,
        @NotBlank String contentType,
        @NotNull @Positive Long fileSize
) {
}
