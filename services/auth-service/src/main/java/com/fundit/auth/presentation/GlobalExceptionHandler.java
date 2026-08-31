package com.fundit.auth.presentation;

import com.fundit.auth.domain.AuthErrorCode;
import com.fundit.auth.domain.account.AccountLockedException;
import com.fundit.common.error.ErrorResponse;
import com.fundit.common.webmvc.error.AbstractGlobalExceptionHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends AbstractGlobalExceptionHandler {

    /**
     * 계정 잠금 시 잠금 해제 예정 시각을 안내해야 하는데(AuthFunctionalSpec.md AUTH-001),
     * AbstractGlobalExceptionHandler.handleBusinessException()은 errorCode의 정적 메시지만
     * 응답에 싣고 detail은 쓰지 않는다. modules:common-webmvc를 건드리지 않고
     * 여기서만 lockedUntil을 detail로 실어 응답한다.
     */
    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ErrorResponse> handleAccountLocked(AccountLockedException e) {
        return ResponseEntity
                .status(AuthErrorCode.ACCOUNT_LOCKED.getHttpStatus())
                .body(ErrorResponse.of(AuthErrorCode.ACCOUNT_LOCKED, Map.of("lockedUntil", e.getLockedUntil())));
    }
}
