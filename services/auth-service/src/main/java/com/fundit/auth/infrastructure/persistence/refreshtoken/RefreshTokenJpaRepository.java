package com.fundit.auth.infrastructure.persistence.refreshtoken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, UUID> {

    /**
     * 회전(rotation)의 확인+즉시폐기를 원자적으로 처리.
     * 주의: 일부러 @Modifying을 붙이지 않았다 — DML(RETURNING)이지만 Hibernate가
     * executeQuery()로 실행해 RETURNING 결과를 그대로 읽어오게 하기 위함
     * (@Modifying을 붙이면 executeUpdate()로 실행되어 영향받은 행 수(int)만 반환되고
     * RETURNING 값을 받을 수 없다). RefreshTokenJpaRepositoryIntegrationTest로 반드시 검증.
     */
    @Modifying
    @Query(value = "DELETE FROM refresh_tokens WHERE token_id = :tokenId AND expires_at > now() RETURNING account_id", nativeQuery = true)
    Optional<UUID> deleteAndReturnAccountId(@Param("tokenId") UUID tokenId);

    @Modifying
    @Query(value = "DELETE FROM refresh_tokens WHERE account_id = :accountId", nativeQuery = true)
    void deleteAllByAccountId(@Param("accountId") UUID accountId);
}
