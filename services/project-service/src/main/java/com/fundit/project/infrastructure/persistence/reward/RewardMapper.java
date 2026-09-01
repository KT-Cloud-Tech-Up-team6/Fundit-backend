package com.fundit.project.infrastructure.persistence.reward;

import com.fundit.project.domain.reward.Reward;
import org.springframework.stereotype.Component;

@Component
public class RewardMapper {

    Reward toDomain(RewardJpaEntity entity) {
        return Reward.builder()
                .id(entity.getId())
                .projectId(entity.getProjectId())
                .name(entity.getName())
                .description(entity.getDescription())
                .price(entity.getPrice())
                .unlimited(Boolean.TRUE.equals(entity.getUnlimited()))
                .earlyBird(Boolean.TRUE.equals(entity.getEarlyBird()))
                .simpleRefundDisabled(Boolean.TRUE.equals(entity.getSimpleRefundDisabled()))
                .categoryType(entity.getCategoryType())
                .disclosure(entity.getDisclosure())
                .displayOrder(entity.getDisplayOrder() == null ? 0 : entity.getDisplayOrder())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .deletedAt(entity.getDeletedAt())
                .build();
    }

    RewardJpaEntity toEntity(Reward domain) {
        return RewardJpaEntity.builder()
                .id(domain.getId())
                .projectId(domain.getProjectId())
                .name(domain.getName())
                .description(domain.getDescription())
                .price(domain.getPrice())
                .unlimited(domain.isUnlimited())
                .earlyBird(domain.isEarlyBird())
                .simpleRefundDisabled(domain.isSimpleRefundDisabled())
                .categoryType(domain.getCategoryType())
                .disclosure(domain.getDisclosure())
                .displayOrder(domain.getDisplayOrder())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .deletedAt(domain.getDeletedAt())
                .build();
    }
}
