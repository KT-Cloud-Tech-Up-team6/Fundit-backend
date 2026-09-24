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
    INVALID_REWARD_SHIPPING_INFO(400, "리워드 배송비/예상 발송일 설정이 올바르지 않습니다."),
    INVALID_EARLY_BIRD_DISCOUNT(400, "얼리버드 할인 설정이 올바르지 않습니다."),
    PRIVACY_CONSENT_REQUIRED(422, "개인정보 수집 동의가 필요합니다."),
    PROJECT_NOT_DELETABLE(422, "준비중 상태의 프로젝트만 삭제할 수 있습니다."),
    PROJECT_NOT_SUBMITTABLE(422, "필수 작성 항목이 완료되지 않아 공개할 수 없습니다."),
    UNSUPPORTED_MEDIA_TYPE(400, "지원하지 않는 파일 형식입니다."),
    MEDIA_TOO_LARGE(400, "파일 용량이 허용 범위를 초과했습니다."),
    INVALID_MEDIA_URL(400, "업로드가 확인되지 않았거나 올바르지 않은 파일 주소입니다."),
    INVALID_PROJECT_DATA(422, "Funding Story 생성에 필요한 프로젝트 정보가 올바르지 않습니다."),
    NOT_READY_TO_GENERATE(422, "Funding Story 요약 확인이 필요합니다."),
    LIVE_QUESTION_SUMMARY_NOT_FOUND(404, "등록되지 않은 LIVE 질문입니다."),
    LIVE_VERIFICATION_ALREADY_EXISTS(409, "이미 답변을 등록한 질문입니다.");

    private final int httpStatus;
    private final String message;

    ProjectErrorCode(int httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
