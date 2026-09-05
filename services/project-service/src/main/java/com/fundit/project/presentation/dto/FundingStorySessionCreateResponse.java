package com.fundit.project.presentation.dto;

import java.util.UUID;

public record FundingStorySessionCreateResponse(UUID sessionId, String status) {
}
