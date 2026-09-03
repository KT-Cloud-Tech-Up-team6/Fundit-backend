package com.fundit.auth.domain;

import com.fundit.common.error.ErrorCode;
import lombok.Getter;

@Getter
public enum AuthErrorCode implements ErrorCode {

    INVALID_CREDENTIALS(401, "이메일 또는 비밀번호가 일치하지 않습니다."),
    ACCOUNT_LOCKED(423, "계정이 잠겨 있습니다."),
    EMAIL_ALREADY_EXISTS(409, "이미 가입된 이메일입니다.");

    private final int httpStatus;
    private final String message;

    AuthErrorCode(int httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
