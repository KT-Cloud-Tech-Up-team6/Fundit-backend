package com.fundit.project.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record PrivacyConsentRequest(@NotNull Boolean agreed) {
}
