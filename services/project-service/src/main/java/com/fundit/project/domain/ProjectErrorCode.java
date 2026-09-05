package com.fundit.project.domain;

import com.fundit.common.error.ErrorCode;
import lombok.Getter;

/**
 * project-service 도메인 전용 에러 코드(서비스당 flat enum 1개 — error-handling.md 컨벤션).
 * INVALID_INPUT/UNAUTHORIZED/FORBIDDEN/NOT_FOUND/DEPENDENCY_FAILURE 등은
 * CommonErrorCode를 그대로 쓰고 여기서 재정의하지 않는다.
 */
@Getter
public enum ProjectErrorCode implements ErrorCode {

    GOAL_AMOUNT_TOO_LOW(400, "목표 금액은 50만원 이상이어야 합니다."),
    INVALID_CATEGORY(400, "존재하지 않는 카테고리 조합입니다."),
    INVALID_REWARD_QUANTITY(400, "리워드 수량 설정이 올바르지 않습니다."),
    PRIVACY_CONSENT_REQUIRED(422, "개인정보 수집 동의가 필요합니다."),
    PROJECT_NOT_DELETABLE(422, "준비중 상태의 프로젝트만 삭제할 수 있습니다."),
    PROJECT_NOT_SUBMITTABLE(422, "필수 작성 항목이 완료되지 않아 심사에 제출할 수 없습니다."),
    PROJECT_NOT_REVIEWABLE(422, "심사 대기 중인 프로젝트만 심사 처리할 수 있습니다.");

    private final int httpStatus;
    private final String message;

    ProjectErrorCode(int httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
