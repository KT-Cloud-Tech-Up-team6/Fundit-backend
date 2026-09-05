package com.fundit.project.presentation.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectStoryResponse(UUID projectId, Instant updatedAt) {
}
