package com.fundit.auth.infrastructure.persistence.passwordresettoken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenJpaRepository extends JpaRepository<PasswordResetTokenJpaEntity, UUID> {

    /**
     * 확인과 폐기를 한 문장으로 처리한다 — 조회 후 삭제로 나누면 같은 토큰을 동시에 제출했을 때
     * 둘 다 통과해 비밀번호가 두 번 바뀐다. {@code RefreshTokenJpaRepository}와 같은 방식이고,
     * 같은 이유로 {@code @Modifying}을 붙이지 않는다(붙이면 RETURNING 값을 받을 수 없다).
     */
    @Query(value = "DELETE FROM password_reset_tokens WHERE token_id = :tokenId AND expires_at > now() "
            + "RETURNING account_id", nativeQuery = true)
    Optional<UUID> deleteAndReturnAccountId(@Param("tokenId") UUID tokenId);

    /** 재발급 시 이전 링크를 무효화한다 — 메일함에 쌓인 옛 링크가 계속 살아있으면 안 된다. */
    @Modifying
    @Query(value = "DELETE FROM password_reset_tokens WHERE account_id = :accountId", nativeQuery = true)
    void deleteAllByAccountId(@Param("accountId") UUID accountId);
}
