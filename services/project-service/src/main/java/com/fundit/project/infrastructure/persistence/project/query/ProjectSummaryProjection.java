package com.fundit.project.infrastructure.persistence.project.query;

import java.util.UUID;

/**
 * persistence-convention.md §3 — 목록 조회 전용 프로젝션. order-service가 주문 목록(V03)에
 * 창작자명·썸네일을 채우기 위해 배치로 호출하는 내부 API({@code GET /internal/projects/summaries})용.
 */
public interface ProjectSummaryProjection {
    UUID getPublicId();
    UUID getSellerId();
    String getTitle();
    String getThumbnailUrl();
}
