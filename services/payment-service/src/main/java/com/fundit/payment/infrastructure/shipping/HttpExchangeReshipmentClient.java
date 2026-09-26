package com.fundit.payment.infrastructure.shipping;

import com.fundit.common.auth.AuthHeaders;
import com.fundit.common.error.DependencyFailureException;
import com.fundit.payment.application.reshipment.ExchangeReshipmentClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.UUID;

/**
 * fulfillment-service 재발송 요청 내부 API({@code POST /internal/fundings/{fundingId}/reshipments})
 * 실제 구현체. 배송 상태 조회({@link HttpShippingStatusClient})와 같은 RestClient·같은 모드
 * 스위치를 공유한다 — 어차피 같은 fulfillment 연동이라 플래그를 따로 두지 않는다
 * (payment-service CLAUDE.md "연동 현황"의 OrderSettlementAggregateClient와 같은 선례).
 */
@Component
@ConditionalOnProperty(prefix = "fulfillment.integration.shipping-status-client", name = "mode", havingValue = "http")
public class HttpExchangeReshipmentClient implements ExchangeReshipmentClient {

    private final RestClient fulfillmentServiceRestClient;
    private final String internalApiKey;

    public HttpExchangeReshipmentClient(RestClient fulfillmentServiceRestClient,
                                         @Value("${internal-api.key}") String internalApiKey) {
        this.fulfillmentServiceRestClient = fulfillmentServiceRestClient;
        this.internalApiKey = internalApiKey;
    }

    @Override
    public ReshipmentResult request(UUID orderId, Long refundRequestId) {
        try {
            ReshipmentResult response = fulfillmentServiceRestClient.post()
                    .uri("/internal/fundings/{fundingId}/reshipments", orderId)
                    .header(AuthHeaders.INTERNAL_API_KEY, internalApiKey)
                    .body(Map.of("refundRequestId", refundRequestId))
                    .retrieve()
                    .body(ReshipmentResult.class);
            if (response == null) {
                throw new DependencyFailureException(new IllegalStateException("fulfillment-service 응답 본문 없음"));
            }
            return response;
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }
}
