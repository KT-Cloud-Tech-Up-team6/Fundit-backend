package com.fundit.project.presentation.dto;

import java.time.Instant;

public record LiveVerificationUpdateResponse(Long liveVerificationId, String answer, Instant updatedAt) {
}
