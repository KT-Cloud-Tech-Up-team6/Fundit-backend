package com.fundit.fulfillment.infrastructure.funding;

import com.fundit.fulfillment.application.funding.OrderFundingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * order-service 내부 API({@code GET /internal/fundings/{fundingId}})가 아직 없어 고정값을
 * 반환하는 개발/테스트용 구현체(payment-service {@code StubOrderFundingClient}와 동일 성격).
 *
 * <p>fundingId를 시드로 결정적(deterministic) 값을 만든다 — projectId는 임의로 fundingId를
 * 그대로 사용한다(실제 매핑과 무관, 로컬 개발 편의용). 실제 연동 이슈에서 order-service
 * 엔드포인트가 준비되면 {@code order.integration.funding-client.mode=http}로 전환한다
 * ({@link HttpOrderFundingClient}).
 */
@Component
@ConditionalOnProperty(prefix = "order.integration.funding-client", name = "mode",
        havingValue = "stub", matchIfMissing = true)
public class StubOrderFundingClient implements OrderFundingClient {

    private static final Logger log = LoggerFactory.getLogger(StubOrderFundingClient.class);

    @Override
    public FundingSnapshot fetch(Long fundingId) {
        log.warn("[STUB] order-service 내부 API 미구현 — 고정값으로 대체합니다. fundingId={}", fundingId);
        return new FundingSnapshot(fundingId, new UUID(0L, fundingId));
    }
}
