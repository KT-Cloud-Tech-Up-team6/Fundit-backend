package com.fundit.payment.infrastructure.persistence.refund.query;

import java.time.Instant;

/** PAYMENT-003 조회 전용 프로젝션(persistence-convention.md §3) — 도메인 재구성 없이 바로 응답용. */
public interface RefundSummaryProjection {

    Long getId();

    Long getFundingId();

    String getTriggerType();

    String getStatus();

    long getAmount();

    Instant getRequestedAt();
}
