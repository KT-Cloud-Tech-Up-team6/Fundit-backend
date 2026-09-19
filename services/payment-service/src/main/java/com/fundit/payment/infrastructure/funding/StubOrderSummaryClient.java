package com.fundit.payment.infrastructure.funding;

import com.fundit.payment.application.refund.OrderSummaryClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** order-service 내부 API가 꺼져있을 때(기본값 stub) 쓰는 스텁 — 항상 빈 맵(부가 정보 없이 진행). */
@Component
@ConditionalOnProperty(prefix = "order.integration.funding-client", name = "mode",
        havingValue = "stub", matchIfMissing = true)
public class StubOrderSummaryClient implements OrderSummaryClient {

    @Override
    public Map<UUID, OrderSummary> fetchBatch(List<UUID> orderIds) {
        return Map.of();
    }
}
