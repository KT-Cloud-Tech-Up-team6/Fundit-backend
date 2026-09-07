package com.fundit.project.presentation.dto;

import java.util.Map;

public record RewardDisclosureResponse(Long rewardId, String categoryType, Map<String, String> disclosure) {
}
