package com.fundit.auth.infrastructure.persistence.refreshtoken;

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
 * 단순 애그리거트(persistence-convention.md §2) — 값 저장·조회만 하므로
 * domain/Mapper/Adapter 없이 이 JpaEntity를 application이 직접 사용한다.
 */
@Getter
@Entity
@Builder
@Table(name = "refresh_tokens")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshTokenJpaEntity {

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
