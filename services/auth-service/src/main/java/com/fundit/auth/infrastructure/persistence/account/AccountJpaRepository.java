package com.fundit.auth.infrastructure.persistence.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccountJpaRepository extends JpaRepository<AccountJpaEntity, UUID> {

    /**
     * 이메일은 암호문으로 저장돼 평문 비교가 안 된다 — 블라인드 인덱스로 찾는다.
     * 평문 → 해시 변환은 {@code AccountPersistenceAdapter}가 한다.
     */
    Optional<AccountJpaEntity> findByEmailHash(String emailHash);

    boolean existsByEmailHash(String emailHash);

    /** 이메일 찾기(AUTH-009). 동명이인이 있을 수 있어 이름까지 같이 본다. */
    Optional<AccountJpaEntity> findByPhoneHashAndNameHash(String phoneHash, String nameHash);

    Optional<AccountJpaEntity> findBySocialProviderAndSocialId(String socialProvider, String socialId);

    /**
     * TokenRefreshService의 회전/재사용탐지 경합을 계정 단위로 직렬화하기 위한 전용 락 조회.
     * 다른 곳(findById)에는 영향 없도록 별도 메서드로 분리한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountJpaEntity a where a.id = :id")
    Optional<AccountJpaEntity> findByIdForUpdate(@Param("id") UUID id);
}
