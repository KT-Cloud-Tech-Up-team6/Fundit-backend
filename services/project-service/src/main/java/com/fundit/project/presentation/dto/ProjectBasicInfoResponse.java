package com.fundit.project.presentation.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectBasicInfoResponse(
        UUID projectId,
        String businessType,
        String categoryMajor,
        String categoryMinor,
        String title,
        Long goalAmount,
        Instant updatedAt
) {
}
