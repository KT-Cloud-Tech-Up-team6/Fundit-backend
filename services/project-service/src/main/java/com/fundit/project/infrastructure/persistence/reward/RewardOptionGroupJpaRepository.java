package com.fundit.project.infrastructure.persistence.reward;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RewardOptionGroupJpaRepository extends JpaRepository<RewardOptionGroupJpaEntity, Long> {

    List<RewardOptionGroupJpaEntity> findByRewardId(Long rewardId);

    List<RewardOptionGroupJpaEntity> findByRewardIdAndDeletedAtIsNullOrderBySortOrderAsc(Long rewardId);

    void deleteByRewardId(Long rewardId);
}
