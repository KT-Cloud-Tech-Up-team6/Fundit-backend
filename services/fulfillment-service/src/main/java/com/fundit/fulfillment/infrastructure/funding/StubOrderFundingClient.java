package com.fundit.fulfillment.infrastructure.funding;

import com.fundit.fulfillment.application.funding.OrderFundingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * order-service 내부 API가 비활성 모드(stub)일 때 쓰는 개발/테스트용 구현체.
 * 식별자를 시드로 결정적(deterministic) 값을 만든다(실제 매핑과 무관, 로컬 개발 편의용).
 */
@Component
@ConditionalOnProperty(prefix = "order.integration.funding-client", name = "mode",
        havingValue = "stub", matchIfMissing = true)
public class StubOrderFundingClient implements OrderFundingClient {

    private static final Logger log = LoggerFactory.getLogger(StubOrderFundingClient.class);

    @Override
    public FundingSnapshot fetch(UUID orderId) {
        log.warn("[STUB] order-service 내부 API 미구현 — 고정값으로 대체합니다. orderId={}", orderId);
        return new FundingSnapshot(new UUID(1L, orderId.getLeastSignificantBits()),
                new UUID(0L, orderId.getLeastSignificantBits()), orderId);
    }

    @Override
    public FundingSnapshot fetchByInternalId(Long fundingId) {
        log.warn("[STUB] order-service 내부 API 미구현 — 고정값으로 대체합니다. fundingId={}", fundingId);
        return new FundingSnapshot(new UUID(1L, fundingId), new UUID(0L, fundingId), new UUID(2L, fundingId));
    }
}
