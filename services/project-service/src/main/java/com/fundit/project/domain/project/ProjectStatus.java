package com.fundit.project.domain.project;

public enum ProjectStatus {

    DRAFT,
    PENDING_REVIEW,
    ONGOING,
    SUCCEEDED,
    FAILED;

    /** 소유자가 아니어도 조회 가능한 상태인지. DRAFT/PENDING_REVIEW는 존재 자체를 숨긴다. */
    public boolean isPublic() {
        return this != DRAFT && this != PENDING_REVIEW;
    }
}
