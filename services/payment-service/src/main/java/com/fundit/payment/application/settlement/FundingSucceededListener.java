package com.fundit.payment.application.settlement;

import java.time.Instant;
import java.util.UUID;

/**
 * PAYMENT-013 — order-service의 {@code FundingSucceeded}(ORDER-006) 구독 포트.
 *
 * <p>[CLAUDE.md 원안 대비 확장] order-service {@code FundingEventPublisher.FundingSucceededEvent}는
 * {@code (fundingId, projectId)}만 갖는다. 이 서비스는 정산 배치를 판매자 단위로 묶어야 해서
 * {@code sellerId}가, "달성확정일+5영업일"을 계산하려면 {@code achievedAt}이 반드시 필요하다 —
 * order-service 실제 이벤트에 이 두 필드가 없다면 이 서비스가 별도로 조회해서 채워야 하므로,
 * 브로커 배선 시점에 반드시 확인해야 한다[정책 확인 필요].
 */
public interface FundingSucceededListener {

    void onFundingSucceeded(FundingSucceededEvent event);

    record FundingSucceededEvent(Long fundingId, Long projectId, UUID sellerId, Instant achievedAt) {
    }
}
