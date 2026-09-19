package com.fundit.payment.infrastructure.shipping;

import com.fundit.payment.application.refund.ShippingStatusClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * fulfillment-service 내부 API 연동이 꺼져있을 때(기본값) 쓰는 스텁 — 항상 "미발송"으로
 * 응답해 PAYMENT-008이 최소한 동작은 하게 해둔다(`HttpOrderFundingClient`류와 동일하게
 * mode: http일 때는 비활성화되고 {@link HttpShippingStatusClient}가 대신 뜬다).
 */
@Component
@ConditionalOnProperty(prefix = "fulfillment.integration.shipping-status-client", name = "mode",
        havingValue = "stub", matchIfMissing = true)
public class StubShippingStatusClient implements ShippingStatusClient {

    private static final Logger log = LoggerFactory.getLogger(StubShippingStatusClient.class);

    @Override
    public ShippingStatus fetch(Long fundingId) {
        log.warn("[STUB] fulfillment-service 내부 API 미연동 — 항상 미발송으로 간주합니다. fundingId={}", fundingId);
        return new ShippingStatus(false, false, null, null);
    }

    @Override
    public ShippingStatus fetch(UUID orderId) {
        log.warn("[STUB] fulfillment-service 내부 API 미연동 — 항상 미발송으로 간주합니다. orderId={}", orderId);
        return new ShippingStatus(false, false, null, null);
    }
}
