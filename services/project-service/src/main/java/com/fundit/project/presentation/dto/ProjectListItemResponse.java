package com.fundit.project.presentation.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectListItemResponse(
        UUID projectId,
        String projectDisplayCode,
        String title,
        String thumbnailUrl,
        String status,
        Instant createdAt,
        Instant fundingDeadline
) {
}
