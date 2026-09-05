package com.fundit.project.infrastructure.persistence.reward;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RewardJpaRepository extends JpaRepository<RewardJpaEntity, Long> {

    Optional<RewardJpaEntity> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByProjectIdAndDeletedAtIsNull(Long projectId);

    List<RewardJpaEntity> findByProjectIdAndDeletedAtIsNullOrderBySortOrderAsc(Long projectId);
}
