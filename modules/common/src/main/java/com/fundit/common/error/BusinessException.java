package com.fundit.common.error;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반 시 던지는 예외. application/domain 계층에서 사용한다.
 * (규칙: 에러 코드/예외 처리 규칙 §예외 던지기)
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * 응답 바디의 {@code detail}에 실릴 값. 대부분 null이고, 클라이언트가 분기해야 하는
     * 부가 정보가 있을 때만 채운다(예: 어느 소셜 제공자로 가입된 계정인지).
     * 사람이 읽는 문장이 아니라 <b>기계가 읽는 값</b>을 넣는다 — 메시지 문자열을 파싱하게 만들지 않는다.
     */
    private final Object detail;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage(), null);
    }

    /** 기본 메시지 대신 상황별 메시지를 쓰고 싶을 때 */
    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public BusinessException(ErrorCode errorCode, String message, Object detail) {
        super(message);
        this.errorCode = errorCode;
        this.detail = detail;
    }

}