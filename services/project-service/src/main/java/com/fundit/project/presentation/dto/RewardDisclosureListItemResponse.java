package com.fundit.project.presentation.dto;

import java.util.Map;

public record RewardDisclosureListItemResponse(Long rewardId, String rewardName, String categoryType, Map<String, String> disclosure) {
}
