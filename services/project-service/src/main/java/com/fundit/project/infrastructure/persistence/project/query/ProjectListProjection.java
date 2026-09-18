package com.fundit.project.infrastructure.persistence.project.query;

import java.time.Instant;
import java.util.UUID;

/**
 * persistence-convention.md §3 — 목록 조회 전용 프로젝션. 도메인 재구성 없이
 * 화면에 뿌릴 값만 바로 조회한다(GET /api/v1/projects, PROJECT-001).
 */
public interface ProjectListProjection {
    /** 내부 PK — 펀딩 현황 스냅샷 배치 조회 조인 키로만 쓰고 응답에는 노출하지 않는다. */
    Long getId();
    UUID getProjectId();
    String getProjectDisplayCode();
    String getTitle();
    String getThumbnailUrl();
    String getStatus();
    Instant getCreatedAt();
    Instant getFundingStartAt();
    Instant getFundingDeadline();
    Long getGoalAmount();
    String getCategoryMajor();
    String getCategoryMinor();
}
