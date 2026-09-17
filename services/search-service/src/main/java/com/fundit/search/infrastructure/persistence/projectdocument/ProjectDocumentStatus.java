package com.fundit.search.infrastructure.persistence.projectdocument;

/** project-service {@code Project.isPublic()} 이후 상태만 존재한다 — DRAFT/PENDING_REVIEW는 색인 대상이 아니다. */
public enum ProjectDocumentStatus {
    ONGOING,
    SUCCEEDED,
    FAILED
}
