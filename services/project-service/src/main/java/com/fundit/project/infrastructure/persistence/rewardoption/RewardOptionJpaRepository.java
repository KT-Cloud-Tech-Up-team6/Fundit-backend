package com.fundit.project.infrastructure.persistence.rewardoption;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RewardOptionJpaRepository extends JpaRepository<RewardOptionJpaEntity, Long> {

    Optional<RewardOptionJpaEntity> findByIdAndDeletedAtIsNull(Long id);

    List<RewardOptionJpaEntity> findByRewardIdAndDeletedAtIsNullOrderByIdAsc(Long rewardId);

    List<RewardOptionJpaEntity> findByRewardIdInAndDeletedAtIsNullOrderByIdAsc(Collection<Long> rewardIds);

    /** sku는 소프트 삭제된 행까지 포함해 전역 유니크(uq_reward_options_sku)라 deleted_at을 걸지 않는다. */
    boolean existsBySku(String sku);
}
