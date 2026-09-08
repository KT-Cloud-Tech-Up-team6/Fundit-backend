package com.fundit.project.presentation.dto;

import java.time.Instant;
import java.util.UUID;

public record FundingStoryApplyResponse(UUID projectId, Instant appliedAt) {
}
