package com.fundit.live.domain;

import com.fundit.common.error.ErrorCode;
import lombok.Getter;

/**
 * live-service 도메인 에러 코드(서비스당 flat enum 1개 — error-handling.md).
 *
 * <p>여기 없는 것은 {@code CommonErrorCode}로 충분하다 — {@code NOT_FOUND}(없는 LIVE),
 * {@code FORBIDDEN}(타인 소유), {@code INVALID_INPUT}, {@code CONFLICT}(상태 위반)는
 * 이미 있으니 재정의하지 않는다.
 *
 * <p>AI 연동 에러 3종({@code CUE_SHEET_NOT_READY} 등)은 AI 어댑터를 붙일 때 추가한다 —
 * 지금 넣으면 아무도 던지지 않는 값이 된다.
 */
@Getter
public enum LiveErrorCode implements ErrorCode {

    /** IVS 송출 시작/종료 실패. 상태는 ERROR로 남고 error_detail에 사유를 적는다. */
    STREAM_FAILED(503, "방송 송출에 실패했습니다."),
    ;

    private final int httpStatus;
    private final String message;

    LiveErrorCode(int httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
