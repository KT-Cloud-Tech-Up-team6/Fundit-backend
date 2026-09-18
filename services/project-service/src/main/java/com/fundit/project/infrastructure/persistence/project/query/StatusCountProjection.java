package com.fundit.project.infrastructure.persistence.project.query;

/** 상태별 프로젝트 개수 집계 전용 프로젝션(GET /api/v1/projects/status-counts). */
public interface StatusCountProjection {
    String getStatus();
    Long getCount();
}
