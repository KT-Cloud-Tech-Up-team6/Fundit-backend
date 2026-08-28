package com.fundit.common.error;

import lombok.Getter;

/**
 * 외부 연동(PG, AI, 스트리밍, Auth, Redis 등) 실패를 감싸는 예외.
 * infrastructure 계층에서 사용한다. (규칙: 에러 코드/예외 처리 규칙 §예외 던지기)
 *
 * 기본은 CommonErrorCode.DEPENDENCY_FAILURE(503)를 쓰고,
 * 더 구체적으로 표현하고 싶으면 서비스에서 정의한 ErrorCode를 넘기면 된다.
 */
@Getter
public class DependencyFailureException extends RuntimeException {

    private final ErrorCode errorCode;

    public DependencyFailureException(Throwable cause) {
        this(CommonErrorCode.DEPENDENCY_FAILURE, cause);
    }

    public DependencyFailureException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

}