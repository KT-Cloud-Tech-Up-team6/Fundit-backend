package com.fundit.project.domain;

import com.fundit.common.error.ErrorCode;
import lombok.Getter;

/**
 * project-service 도메인 전용 에러 코드.
 * 어떤 코드가 필요한지는 docs/ProjectFunctionalSpec.md의 "에러 코드 매핑" 표가 기준이다.
 * INVALID_INPUT/UNAUTHORIZED/FORBIDDEN/NOT_FOUND/CONFLICT/RESOURCE_LOCKED/DEPENDENCY_FAILURE는
 * CommonErrorCode에 이미 있으므로 여기서 재정의하지 않는다.
 */
@Getter
public enum ProjectErrorCode implements ErrorCode {

    PRIVACY_NOT_AGREED(400, "프로젝트 개설을 위한 개인정보 수집에 동의해야 합니다."),
    INVALID_CATEGORY(400, "선택한 카테고리 대분류/상세분류 조합이 존재하지 않습니다."),
    DUPLICATE_SKU(409, "이미 사용 중인 SKU입니다. 다른 값으로 다시 시도해 주세요."),
    REWARD_HAS_ACTIVE_FUNDING(409, "펀딩 참여 이력이 있어 삭제할 수 없습니다."),
    PROJECT_REVIEW_NOT_PENDING(409, "검수 대기 중인 프로젝트가 아닙니다."),
    PROJECT_NOT_DELETABLE(422, "검수 중이거나 진행 중인 프로젝트는 삭제할 수 없습니다."),
    SUPPORTER_REVIEW_NOT_ELIGIBLE(422, "배송이 완료된 펀딩 건에만 후기를 작성할 수 있습니다.");

    private final int httpStatus;
    private final String message;

    ProjectErrorCode(int httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
