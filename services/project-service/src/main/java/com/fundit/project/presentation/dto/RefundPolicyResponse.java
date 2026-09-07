package com.fundit.project.presentation.dto;

import java.util.List;

public record RefundPolicyResponse(CommonRefundPolicyResponse commonPolicy, List<RewardRefundPolicyResponse> rewardPolicies) {
}
