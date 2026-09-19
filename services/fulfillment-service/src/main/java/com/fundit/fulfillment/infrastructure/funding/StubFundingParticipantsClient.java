package com.fundit.fulfillment.infrastructure.funding;

import com.fundit.fulfillment.application.funding.FundingParticipantsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * order-service 내부 API가 비활성 모드(stub)일 때 쓰는 개발/테스트용 구현체
 * ({@link StubOrderFundingClient}와 동일 성격). projectId를 시드로 결정적 참가자 1명을 만든다.
 */
@Component
@ConditionalOnProperty(prefix = "order.integration.funding-client", name = "mode",
        havingValue = "stub", matchIfMissing = true)
public class StubFundingParticipantsClient implements FundingParticipantsClient {

    private static final Logger log = LoggerFactory.getLogger(StubFundingParticipantsClient.class);

    @Override
    public List<UUID> listParticipantMemberIds(UUID projectId) {
        log.warn("[STUB] order-service 내부 API 미구현 — 고정값으로 대체합니다. projectId={}", projectId);
        return List.of(new UUID(0L, projectId.getLeastSignificantBits()));
    }
}
