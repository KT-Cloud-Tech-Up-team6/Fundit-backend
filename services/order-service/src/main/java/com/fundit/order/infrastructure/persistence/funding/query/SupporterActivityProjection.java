package com.fundit.order.infrastructure.persistence.funding.query;

import java.time.Instant;
import java.util.UUID;

/** ORDER-001 조회 전용 프로젝션(persistence-convention.md §3) — 도메인 재구성 없이 바로 응답용. */
public interface SupporterActivityProjection {

    UUID getMemberId();

    Instant getCreatedAt();

    Long getAmount();
}
