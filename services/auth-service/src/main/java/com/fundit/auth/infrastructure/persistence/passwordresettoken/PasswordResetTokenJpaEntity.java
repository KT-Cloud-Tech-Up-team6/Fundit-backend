package com.fundit.auth.infrastructure.persistence.passwordresettoken;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 비밀번호 재설정 토큰(AUTH-010). {@code refresh_tokens}와 같은 패턴의 단순 애그리거트다
 * (persistence-convention.md §2) — 값 저장·조회만 하므로 domain/Mapper/Adapter를 두지 않는다.
 *
 * <p>테이블은 {@code V1__init_schema.sql}에 이미 있었고 쓰는 코드만 없었다.
 */
@Getter
@Entity
@Builder
@Table(name = "password_reset_tokens")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetTokenJpaEntity {

    @Id
    @Column(name = "token_id")
    private UUID tokenId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
