package com.fundit.project.presentation.dto.reward;

import java.util.List;

/** 리워드 목록·정보고시 조회가 공유하는 {"rewards": [...]} 껍데기. */
public record RewardsResponse<T>(List<T> rewards) {

    public static <T> RewardsResponse<T> of(List<T> rewards) {
        return new RewardsResponse<>(rewards);
    }
}
