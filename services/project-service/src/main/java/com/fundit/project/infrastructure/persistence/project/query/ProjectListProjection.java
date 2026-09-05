package com.fundit.project.infrastructure.persistence.project.query;

import java.time.Instant;
import java.util.UUID;

/**
 * persistence-convention.md §3 — 목록 조회 전용 프로젝션. 도메인 재구성 없이
 * 화면에 뿌릴 값만 바로 조회한다(GET /api/v1/projects, PROJECT-001).
 */
public interface ProjectListProjection {
    UUID getProjectId();
    String getProjectDisplayCode();
    String getTitle();
    String getThumbnailUrl();
    String getStatus();
    Instant getCreatedAt();
    Instant getFundingDeadline();
}
