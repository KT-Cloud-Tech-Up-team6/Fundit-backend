package com.fundit.payment.infrastructure.shipping;

import com.fundit.common.auth.AuthHeaders;
import com.fundit.common.error.DependencyFailureException;
import com.fundit.payment.application.refund.ShippingStatusClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/**
 * fulfillment-service 내부 API(FULFILLMENT-008) 실제 구현체.
 * {@code fulfillment.integration.shipping-status-client.mode=http}로 전환해 활성화한다
 * (order-service {@code HttpOrderFundingClient}와 동일 패턴 — 인증은 동일하게
 * {@link AuthHeaders#INTERNAL_API_KEY} 공유 시크릿).
 */
@Component
@ConditionalOnProperty(prefix = "fulfillment.integration.shipping-status-client", name = "mode", havingValue = "http")
public class HttpShippingStatusClient implements ShippingStatusClient {

    private final RestClient fulfillmentServiceRestClient;
    private final String internalApiKey;

    public HttpShippingStatusClient(RestClient fulfillmentServiceRestClient,
                                     @Value("${internal-api.key}") String internalApiKey) {
        this.fulfillmentServiceRestClient = fulfillmentServiceRestClient;
        this.internalApiKey = internalApiKey;
    }

    @Override
    public ShippingStatus fetch(Long fundingId) {
        return get(fundingId);
    }

    @Override
    public ShippingStatus fetch(UUID orderId) {
        return get(orderId);
    }

    private ShippingStatus get(Object fundingId) {
        try {
            ShippingStatus response = fulfillmentServiceRestClient.get()
                    .uri("/internal/fundings/{fundingId}/fulfillment-status", fundingId)
                    .header(AuthHeaders.INTERNAL_API_KEY, internalApiKey)
                    .retrieve()
                    .body(ShippingStatus.class);
            if (response == null) {
                throw new DependencyFailureException(new IllegalStateException("fulfillment-service 응답 본문 없음"));
            }
            return response;
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }
}
