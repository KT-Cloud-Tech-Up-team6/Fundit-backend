package com.fundit.project.infrastructure.persistence.reward;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RewardOptionValueJpaRepository extends JpaRepository<RewardOptionValueJpaEntity, Long> {

    List<RewardOptionValueJpaEntity> findByOptionGroupIdOrderBySortOrderAsc(Long optionGroupId);

    void deleteByOptionGroupId(Long optionGroupId);
}
