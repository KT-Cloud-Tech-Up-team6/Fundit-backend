package com.fundit.project.application.project;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.domain.project.ProjectStatus;

import java.util.List;

/**
 * 판매자 목록 조회의 status 파라미터를 실제 상태 목록으로 바꾼다.
 * API 명세는 개별 상태값 5개를 받지만, 기능명세의 탭("준비 중"=DRAFT+PENDING_REVIEW,
 * "종료"=SUCCEEDED+FAILED)도 한 번에 조회할 수 있어야 해서 그룹 별칭도 함께 받는다.
 */
public final class ProjectListFilter {

    private static final String PREPARING = "PREPARING";
    private static final String CLOSED = "CLOSED";

    private ProjectListFilter() {
    }

    public static List<ProjectStatus> resolve(String status) {
        if (status == null || status.isBlank()) {
            return List.of();
        }
        String normalized = status.trim().toUpperCase();
        if (PREPARING.equals(normalized)) {
            return List.of(ProjectStatus.DRAFT, ProjectStatus.PENDING_REVIEW);
        }
        if (CLOSED.equals(normalized)) {
            return List.of(ProjectStatus.SUCCEEDED, ProjectStatus.FAILED);
        }
        try {
            return List.of(ProjectStatus.valueOf(normalized));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "지원하지 않는 status 값입니다: " + status);
        }
    }
}
