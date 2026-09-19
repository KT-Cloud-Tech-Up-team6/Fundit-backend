package com.fundit.payment.infrastructure.funding;

import com.fundit.payment.application.funding.OrderFundingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * order-service 내부 API가 꺼져있을 때(기본값) 쓰는 스텁. 식별자를 시드로 결정적 값을 만든다.
 */
@Component
@ConditionalOnProperty(prefix = "order.integration.funding-client", name = "mode",
        havingValue = "stub", matchIfMissing = true)
public class StubOrderFundingClient implements OrderFundingClient {

    private static final Logger log = LoggerFactory.getLogger(StubOrderFundingClient.class);

    @Override
    public FundingSnapshot fetch(UUID orderId) {
        log.warn("[STUB] order-service 내부 API 미구현 — 고정값으로 대체합니다. orderId={}", orderId);
        return new FundingSnapshot(
                new UUID(0L, orderId.getLeastSignificantBits()),
                new UUID(1L, orderId.getLeastSignificantBits()),
                "PENDING",
                10_000L,
                "[스텁] 테스트 주문 #" + orderId,
                null,
                orderId);
    }

    @Override
    public FundingSnapshot fetchByInternalId(Long fundingId) {
        log.warn("[STUB] order-service 내부 API 미구현 — 고정값으로 대체합니다. fundingId={}", fundingId);
        UUID deterministicMemberId = new UUID(0L, fundingId);
        UUID deterministicSellerId = new UUID(1L, fundingId);
        return new FundingSnapshot(
                deterministicMemberId,
                deterministicSellerId,
                "PENDING",
                10_000L,
                "[스텁] 테스트 주문 #" + fundingId,
                null,
                new UUID(2L, fundingId));
    }
}
