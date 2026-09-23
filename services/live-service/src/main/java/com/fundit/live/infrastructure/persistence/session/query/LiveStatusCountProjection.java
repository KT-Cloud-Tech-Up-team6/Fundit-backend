package com.fundit.live.infrastructure.persistence.session.query;

/** 상태별 LIVE 개수 집계 전용 프로젝션(GET /api/v1/lives/status-counts). */
public interface LiveStatusCountProjection {
    String getStatus();
    Long getCount();
}
