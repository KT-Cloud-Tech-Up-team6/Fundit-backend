package com.fundit.auth.domain.account;

import com.fundit.auth.domain.AuthErrorCode;
import com.fundit.common.error.BusinessException;
import lombok.Getter;

import java.time.Instant;

/**
 * 계정 잠금 상태에서 로그인 시도 시 던진다. 잠금 해제 예정 시각을 응답에 안내하기 위해
 * AuthErrorCode.ACCOUNT_LOCKED와 별개로 lockedUntil을 들고 다닌다
 * (GlobalExceptionHandler의 전용 핸들러가 이 값을 detail로 응답에 싣는다).
 */
@Getter
public class AccountLockedException extends BusinessException {

    private final Instant lockedUntil;

    public AccountLockedException(Instant lockedUntil) {
        super(AuthErrorCode.ACCOUNT_LOCKED);
        this.lockedUntil = lockedUntil;
    }
}
