package com.fundit.payment.infrastructure.funding;

import com.fundit.payment.application.funding.OrderFundingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * order-service 내부 API({@code GET /internal/fundings/{fundingId}})가 아직 없어(payment-service
 * CLAUDE.md "가장 시급한 미해결 의존성") 고정값을 반환하는 개발/테스트용 구현체.
 *
 * <p>fundingId를 시드로 결정적(deterministic) 값을 만들어, 같은 fundingId로 여러 번 호출해도
 * 같은 결과가 나오게 했다 — PAYMENT-001의 "재사용" 로직을 로컬에서 확인할 수 있게 하기 위함.
 * 실제 연동 이슈에서 order-service 엔드포인트가 준비되면
 * {@code order.integration.funding-client.mode=http}로 전환한다({@link HttpOrderFundingClient}).
 */
@Component
@ConditionalOnProperty(prefix = "order.integration.funding-client", name = "mode",
        havingValue = "stub", matchIfMissing = true)
public class StubOrderFundingClient implements OrderFundingClient {

    private static final Logger log = LoggerFactory.getLogger(StubOrderFundingClient.class);

    @Override
    public FundingSnapshot fetch(Long fundingId) {
        log.warn("[STUB] order-service 내부 API 미구현 — 고정값으로 대체합니다. fundingId={}", fundingId);
        UUID deterministicMemberId = new UUID(0L, fundingId);
        UUID deterministicSellerId = new UUID(1L, fundingId);
        return new FundingSnapshot(
                deterministicMemberId,
                deterministicSellerId,
                "PENDING",
                10_000L,
                "[스텁] 테스트 주문 #" + fundingId,
                null);
    }
}
