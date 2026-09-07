package com.fundit.project.infrastructure.persistence.reward;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RewardJpaRepository extends JpaRepository<RewardJpaEntity, Long> {

    Optional<RewardJpaEntity> findByIdAndDeletedAtIsNull(Long id);

    /**
     * replaceOptions의 삭제-재삽입 경합을 리워드 단위로 직렬화하기 위한 전용 락 조회.
     * 다른 곳(findById)에는 영향 없도록 별도 메서드로 분리한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RewardJpaEntity r where r.id = :id")
    Optional<RewardJpaEntity> findByIdForUpdate(@Param("id") Long id);

    boolean existsByProjectIdAndDeletedAtIsNull(Long projectId);

    List<RewardJpaEntity> findByProjectIdAndDeletedAtIsNullOrderBySortOrderAsc(Long projectId);
}
