package com.fundit.project.presentation.dto.reward;

import com.fundit.project.domain.reward.Reward;

import java.util.Map;

/** 값이 없는 항목은 null 그대로 내려준다("정보 없음" 표기는 클라이언트 몫). */
public record RewardDisclosureResponse(Long rewardId,
                                       String name,
                                       String categoryType,
                                       Map<String, Object> disclosure) {

    public static RewardDisclosureResponse from(Reward reward) {
        return new RewardDisclosureResponse(
                reward.getId(), reward.getName(), reward.getCategoryType(), reward.getDisclosure());
    }
}
