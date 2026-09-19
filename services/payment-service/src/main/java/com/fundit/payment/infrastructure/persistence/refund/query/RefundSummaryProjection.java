package com.fundit.payment.infrastructure.persistence.refund.query;

import java.time.Instant;
import java.util.UUID;

/** PAYMENT-003 조회 전용 프로젝션(persistence-convention.md §3) — 도메인 재구성 없이 바로 응답용. */
public interface RefundSummaryProjection {

    Long getId();

    UUID getFundingId();

    String getTriggerType();

    String getStatus();

    long getAmount();

    Instant getRequestedAt();

    /** V04 — 환불 신청 사유(자유텍스트, DEFECT는 하자유형 태그가 앞에 붙어 저장됨). */
    String getReasonDetail();

    /** V04 — 반려 시에만 채워진다. */
    String getRejectedReason();

    /** V04 — 처리 완료 시각(취소 승인/거부 등 최종 결정 시각). 처리 전이면 null. */
    Instant getProcessedAt();
}
