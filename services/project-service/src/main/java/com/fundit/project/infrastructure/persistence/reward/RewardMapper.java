package com.fundit.project.infrastructure.persistence.reward;

import com.fundit.project.domain.reward.Reward;
import org.springframework.stereotype.Component;

@Component
class RewardMapper {

    /**
     * 옵션 그룹/값은 담지 않는다 — RewardRepository는 옵션을 별도 replaceOptions()로만 다루고,
     * 이 서비스의 응답(RewardResponse)도 hasOption 여부만 노출해 옵션 목록을 되읽을 필요가 없다.
     */
    Reward toDomain(RewardJpaEntity entity) {
        return Reward.builder()
                .id(entity.getId())
                .projectId(entity.getProjectId())
                .name(entity.getName())
                .description(entity.getDescription())
                .imageUrl(entity.getImageUrl())
                .price(entity.getPrice())
                .isLimited(entity.getIsLimited())
                .quantity(entity.getQuantity())
                .isEarlyBird(entity.getIsEarlyBird())
                .hasOption(entity.getHasOption())
                .sortOrder(entity.getSortOrder())
                .categoryType(entity.getCategoryType())
                .disclosure(entity.getDisclosure())
                .simpleRefundDisabled(entity.getSimpleRefundDisabled())
                .rewardDisplayCode(entity.getRewardDisplayCode())
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
                .imageUrl(domain.getImageUrl())
                .price(domain.getPrice())
                .isLimited(domain.isLimited())
                .quantity(domain.getQuantity())
                .isEarlyBird(domain.isEarlyBird())
                .hasOption(domain.isHasOption())
                .sortOrder(domain.getSortOrder())
                .categoryType(domain.getCategoryType())
                .disclosure(domain.getDisclosure())
                .simpleRefundDisabled(domain.isSimpleRefundDisabled())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .deletedAt(domain.getDeletedAt())
                .build();
    }
}
