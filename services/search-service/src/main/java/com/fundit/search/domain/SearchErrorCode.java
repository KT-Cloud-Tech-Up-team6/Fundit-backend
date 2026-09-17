package com.fundit.search.domain;

import com.fundit.common.error.ErrorCode;
import lombok.Getter;

/**
 * search-service 전용 에러 코드(서비스당 flat enum 1개 — error-handling.md 컨벤션).
 * CommonErrorCode에 이미 있는 INVALID_INPUT/UNAUTHORIZED/NOT_FOUND 등은 재정의하지 않는다
 * (SearchDomainApiSpec.md "에러 코드 매핑" 표 기준).
 */
@Getter
public enum SearchErrorCode implements ErrorCode {

    INVALID_CATEGORY(400, "존재하지 않는 카테고리입니다.");

    private final int httpStatus;
    private final String message;

    SearchErrorCode(int httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
