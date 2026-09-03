package com.fundit.auth.infrastructure.persistence.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccountJpaRepository extends JpaRepository<AccountJpaEntity, UUID> {

    Optional<AccountJpaEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * TokenRefreshService의 회전/재사용탐지 경합을 계정 단위로 직렬화하기 위한 전용 락 조회.
     * 다른 곳(findById)에는 영향 없도록 별도 메서드로 분리한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountJpaEntity a where a.id = :id")
    Optional<AccountJpaEntity> findByIdForUpdate(@Param("id") UUID id);
}
