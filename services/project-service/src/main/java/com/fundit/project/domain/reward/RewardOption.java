package com.fundit.project.domain.reward;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 리워드 옵션. 재고 수량은 이 서비스가 소유하지 않는다(order-service inventories가 source of truth)
 * — 여기에는 재고 컬럼을 두지 않는다.
 * sku는 등록 이후 변경할 수 없다(재고 식별자로 order-service가 참조하고 있음).
 */
@Getter
@Builder
public class RewardOption {

    private final Long id;
    private final Long rewardId;
    private final String sku;

    private String optionName;

    private final Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    public static RewardOption create(Long rewardId, String optionName, String sku) {
        return RewardOption.builder()
                .rewardId(rewardId)
                .optionName(optionName)
                .sku(sku)
                .build();
    }

    public void rename(String optionName) {
        if (optionName != null) {
            this.optionName = optionName;
        }
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
    }
}
