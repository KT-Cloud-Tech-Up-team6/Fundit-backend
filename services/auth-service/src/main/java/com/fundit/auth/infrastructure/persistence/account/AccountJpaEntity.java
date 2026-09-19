package com.fundit.auth.infrastructure.persistence.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code email}에는 <b>암호문</b>이 들어간다(security.md S9). 조회는 평문으로 못 하고
 * {@code emailHash}로 한다 — 변환은 {@code AccountMapper}·{@code AccountPersistenceAdapter}가 맡는다.
 */
@Getter
@Entity
@Builder
@Table(name = "accounts")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountJpaEntity {

    @Id
    private UUID id;

    /** AES-GCM 암호문. 같은 평문도 매번 다른 값이라 이 컬럼으로는 조회할 수 없다. */
    @Column(nullable = false)
    private String email;

    /** 조회용 블라인드 인덱스(HMAC-SHA256 hex). UNIQUE 제약이 평문 대신 여기 걸려 있다. */
    @Column(name = "email_hash", nullable = false, length = 64)
    private String emailHash;

    /**
     * 이메일 찾기(AUTH-009)가 이름+전화번호로 계정을 찾는다. 원문은 member-service가 갖고
     * auth는 해시만 둔다 — 복호화가 불가능하므로 개인정보 보관이 아니라 조회 색인이다.
     */
    @Column(name = "phone_hash", length = 64)
    private String phoneHash;

    @Column(name = "name_hash", length = 64)
    private String nameHash;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "social_provider")
    private String socialProvider;

    @Column(name = "social_id")
    private String socialId;

    @Column(nullable = false)
    private String role;

    @Column(name = "failed_login_count", nullable = false)
    private Integer failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "must_change_password", nullable = false)
    private Boolean mustChangePassword;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.updatedAt == null) this.updatedAt = now;
        if (this.failedLoginCount == null) this.failedLoginCount = 0;
        if (this.mustChangePassword == null) this.mustChangePassword = false;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
