package com.fundit.payment.infrastructure.shipping;

import com.fundit.payment.application.refund.ShippingStatusClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * fulfillment-service의 내부 API(FULFILLMENT-008)가 아직 배선되지 않아 실제 발송 상태를
 * 조회할 방법이 없다. 항상 "미발송"으로 응답해 PAYMENT-008이 최소한 동작은 하게 해둔다 —
 * fulfillment-service 연동이 끝나면 이 구현체를 실제 HTTP 클라이언트로 교체해야 한다.
 */
@Component
public class StubShippingStatusClient implements ShippingStatusClient {

    private static final Logger log = LoggerFactory.getLogger(StubShippingStatusClient.class);

    @Override
    public boolean isAlreadyShipped(Long fundingId) {
        log.warn("[STUB] fulfillment-service 내부 API 미연동 — 항상 미발송으로 간주합니다. fundingId={}", fundingId);
        return false;
    }
}
