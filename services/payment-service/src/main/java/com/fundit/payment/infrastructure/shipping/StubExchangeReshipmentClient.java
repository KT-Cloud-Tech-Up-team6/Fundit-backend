package com.fundit.payment.infrastructure.shipping;

import com.fundit.payment.application.reshipment.ExchangeReshipmentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * fulfillment-service 연동이 꺼져있을 때(기본값) 쓰는 스텁 — 재발송 요청을 성공으로 간주해
 * 교환 승인/교환비 결제 흐름이 로컬에서도 끝까지 동작하게 한다. 실제 재발송은 일어나지 않는다.
 */
@Component
@ConditionalOnProperty(prefix = "fulfillment.integration.shipping-status-client", name = "mode",
        havingValue = "stub", matchIfMissing = true)
public class StubExchangeReshipmentClient implements ExchangeReshipmentClient {

    private static final Logger log = LoggerFactory.getLogger(StubExchangeReshipmentClient.class);

    @Override
    public ReshipmentResult request(UUID orderId, Long refundRequestId) {
        log.warn("[STUB] fulfillment-service 재발송 요청 미연동 — 성공으로 간주합니다. orderId={}, refundRequestId={}",
                orderId, refundRequestId);
        return new ReshipmentResult("PREPARING", 1);
    }
}
