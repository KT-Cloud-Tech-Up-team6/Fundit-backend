package com.fundit.payment.infrastructure.settlement;

import com.fundit.payment.application.settlement.OrderSettlementAggregateClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * order-service에 정산 집계 API가 아직 없어(payment-service CLAUDE.md) 빈 값을 반환하는
 * 개발/테스트용 구현체. 실제 연동 이슈에서 HTTP 구현체로 교체한다({@link com.fundit.payment.infrastructure.funding.HttpOrderFundingClient}와 동일한 전환 방식).
 */
@Component
public class StubOrderSettlementAggregateClient implements OrderSettlementAggregateClient {

    private static final Logger log = LoggerFactory.getLogger(StubOrderSettlementAggregateClient.class);

    @Override
    public List<LineItemAggregate> fetchLineItems(Long fundingId) {
        log.warn("[STUB] order-service 정산 집계 API 미구현 — 빈 목록으로 대체합니다. fundingId={}", fundingId);
        return List.of();
    }

    @Override
    public long fetchMakerCouponDeductionAmount(Long fundingId) {
        log.warn("[STUB] order-service 정산 집계 API 미구현 — 0으로 대체합니다. fundingId={}", fundingId);
        return 0L;
    }
}
