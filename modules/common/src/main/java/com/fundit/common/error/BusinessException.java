package com.fundit.common.error;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반 시 던지는 예외. application/domain 계층에서 사용한다.
 * (규칙: 에러 코드/예외 처리 규칙 §예외 던지기)
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 기본 메시지 대신 상황별 메시지를 쓰고 싶을 때 */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

}