package com.fundit.project.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MediaUploadUrlRequest(
        @NotBlank String fileName,
        @NotBlank String contentType,
        @NotNull @Positive Long fileSize
) {
}
