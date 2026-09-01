package com.fundit.project.fixture;

import com.fundit.project.domain.reward.Reward;
import com.fundit.project.domain.reward.RewardOption;

import java.time.Instant;
import java.util.Map;

public final class RewardFixture {

    public static final Long REWARD_ID = 501L;
    public static final Long OPTION_ID = 9001L;
    public static final String SKU = "SKU-501-WHITE";

    private RewardFixture() {
    }

    public static Reward reward() {
        return base().build();
    }

    public static Reward unlimitedReward() {
        return base().unlimited(true).build();
    }

    public static Reward.RewardBuilder base() {
        return Reward.builder()
                .id(REWARD_ID)
                .projectId(ProjectFixture.PROJECT_ID)
                .name("가습기 기본형")
                .description("설명")
                .price(39_000L)
                .unlimited(false)
                .earlyBird(true)
                .simpleRefundDisabled(false)
                .categoryType("ELECTRONICS")
                .disclosure(Map.of("모델명", "H-100"))
                .displayOrder(0)
                .createdAt(Instant.parse("2026-08-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-08-01T00:00:00Z"));
    }

    public static RewardOption option() {
        return optionBase().build();
    }

    public static RewardOption.RewardOptionBuilder optionBase() {
        return RewardOption.builder()
                .id(OPTION_ID)
                .rewardId(REWARD_ID)
                .optionName("화이트")
                .sku(SKU)
                .createdAt(Instant.parse("2026-08-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-08-01T00:00:00Z"));
    }
}
