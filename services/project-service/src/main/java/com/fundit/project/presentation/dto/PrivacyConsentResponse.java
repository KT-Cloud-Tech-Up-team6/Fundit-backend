package com.fundit.project.presentation.dto;

import java.time.Instant;
import java.util.UUID;

public record PrivacyConsentResponse(UUID projectId, Instant consentedAt) {
}
