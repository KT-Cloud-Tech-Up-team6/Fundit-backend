package com.fundit.auth.domain.account;

import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * 복잡한 애그리거트(persistence-convention.md 기준) — 로그인 실패 카운트/계정 잠금
 * 상태 전이가 불변식에 해당해 도메인/영속성을 완전히 분리한다.
 */
@Getter
@Builder
public class Account {

    private static final int MAX_FAILED_LOGIN_COUNT = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    private final UUID id;
    private final String email;
    private String passwordHash;
    private final String socialProvider;
    private final String socialId;
    private final Role role;
    private int failedLoginCount;
    private Instant lockedUntil;
    private boolean mustChangePassword;
    private final Instant createdAt;
    private Instant updatedAt;

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** 로그인 실패 시 호출. 5회째에 30분 잠금을 걸고 카운터를 리셋한다. */
    public void recordFailedLogin(Instant now) {
        failedLoginCount++;
        if (failedLoginCount >= MAX_FAILED_LOGIN_COUNT) {
            lockedUntil = now.plus(LOCK_DURATION);
            failedLoginCount = 0;
        }
    }

    public void recordSuccessfulLogin() {
        failedLoginCount = 0;
        lockedUntil = null;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.mustChangePassword = false;
    }
}
