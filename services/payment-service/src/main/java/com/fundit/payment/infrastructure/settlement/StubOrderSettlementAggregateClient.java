package com.fundit.payment.infrastructure.settlement;

import com.fundit.payment.application.settlement.OrderSettlementAggregateClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * order-service 정산 집계 API 연동이 꺼져있을 때(기본값) 쓰는 스텁 — 항상 빈 값으로 응답해
 * 로컬에서 order-service 없이도 정산 로직이 최소한 동작은 하게 해둔다({@link com.fundit.payment.infrastructure.shipping.StubShippingStatusClient}류와 동일 패턴).
 * {@code order.integration.funding-client.mode=http}로 전환하면 {@link HttpOrderSettlementAggregateClient}가 대신 뜬다.
 */
@Component
@ConditionalOnProperty(prefix = "order.integration.funding-client", name = "mode",
        havingValue = "stub", matchIfMissing = true)
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
