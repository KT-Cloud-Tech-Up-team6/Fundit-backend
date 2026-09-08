package com.fundit.project.presentation.dto;

import java.util.List;

public record RewardOptionGroupResponse(Long groupId, String groupName, List<RewardOptionValueResponse> values) {
}
