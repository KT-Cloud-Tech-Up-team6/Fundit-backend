package com.fundit.auth.infrastructure.persistence.refreshtoken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, UUID> {

    /**
     * 회전(rotation)의 확인+즉시폐기를 원자적으로 처리.
     * 주의: 일부러 @Modifying을 붙이지 않는다 — DML(RETURNING)이지만 Hibernate가
     * executeQuery()로 실행해 RETURNING 결과를 그대로 읽어오게 하기 위함
     * (@Modifying을 붙이면 executeUpdate()로 실행되어 영향받은 행 수(int)만 반환되고
     * RETURNING 값을 받을 수 없다 — 실제로 @Modifying이 붙어있던 상태로 실기동해보니
     * "Modifying queries can only use void, int/Integer, or long/Long as return type"
     * 예외가 발생해 이 설계가 깨져 있었음을 확인, 2026-08-31 수정).
     */
    @Query(value = "DELETE FROM refresh_tokens WHERE token_id = :tokenId AND expires_at > now() RETURNING account_id", nativeQuery = true)
    Optional<UUID> deleteAndReturnAccountId(@Param("tokenId") UUID tokenId);

    /**
     * @Modifying 쿼리는 SimpleJpaRepository의 기본 CRUD 메서드와 달리 자동으로 트랜잭션이
     * 걸리지 않는다 — 호출부가 트랜잭션 안에 있지 않으면 "No active transaction for update
     * or delete query"로 실패한다(실기동/통합테스트로 확인, 2026-08-31). 호출부(TokenRefreshService)를
     * @Transactional로 감싸는 것과 별개로, 이 메서드 자체도 트랜잭션을 보장해 어디서 호출해도
     * 안전하게 만든다.
     *
     * REQUIRES_NEW인 이유: TokenRefreshService.refresh()가 계정 단위 락을 걸기 위해
     * 자체적으로 @Transactional이 됐다(동시성 회귀 수정, PR 리뷰 지적 반영) — 이 메서드가
     * REQUIRES_NEW가 아니면 그 트랜잭션에 합류해버려서, 재사용 탐지 후 던지는 예외로
     * 바깥 트랜잭션이 롤백될 때 방금 실행한 전체 세션 삭제까지 같이 취소된다(버그3 재발).
     * 어떤 예외가 나든 이 삭제만은 독립적으로 즉시 커밋되어야 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query(value = "DELETE FROM refresh_tokens WHERE account_id = :accountId", nativeQuery = true)
    void deleteAllByAccountId(@Param("accountId") UUID accountId);
}
