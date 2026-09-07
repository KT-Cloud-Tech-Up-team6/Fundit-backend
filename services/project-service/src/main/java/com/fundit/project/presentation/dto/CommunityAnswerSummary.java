package com.fundit.project.presentation.dto;

import java.time.Instant;

public record CommunityAnswerSummary(String content, Instant updatedAt) {
}
