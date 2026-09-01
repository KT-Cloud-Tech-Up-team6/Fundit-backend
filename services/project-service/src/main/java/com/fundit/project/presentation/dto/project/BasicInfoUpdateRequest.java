package com.fundit.project.presentation.dto.project;

import com.fundit.project.domain.project.BusinessType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BasicInfoUpdateRequest(@NotNull BusinessType businessType,
                                     @NotBlank String categoryMajor,
                                     @NotBlank String categoryMinor,
                                     @NotNull Long goalAmount,
                                     @NotNull Boolean privacyAgreed) {
}
