package com.fundit.project.infrastructure.persistence.rewardoption;

import com.fundit.project.domain.reward.RewardOption;
import org.springframework.stereotype.Component;

@Component
public class RewardOptionMapper {

    RewardOption toDomain(RewardOptionJpaEntity entity) {
        return RewardOption.builder()
                .id(entity.getId())
                .rewardId(entity.getRewardId())
                .optionName(entity.getOptionName())
                .sku(entity.getSku())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .deletedAt(entity.getDeletedAt())
                .build();
    }

    RewardOptionJpaEntity toEntity(RewardOption domain) {
        return RewardOptionJpaEntity.builder()
                .id(domain.getId())
                .rewardId(domain.getRewardId())
                .optionName(domain.getOptionName())
                .sku(domain.getSku())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .deletedAt(domain.getDeletedAt())
                .build();
    }
}
