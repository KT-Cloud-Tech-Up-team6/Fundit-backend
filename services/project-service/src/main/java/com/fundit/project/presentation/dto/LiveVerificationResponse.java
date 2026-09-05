package com.fundit.project.presentation.dto;

import java.time.Instant;

public record LiveVerificationResponse(Long liveVerificationId, String answer, Instant createdAt) {
}
