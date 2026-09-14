package com.fundit.fulfillment.infrastructure.funding;

import com.fundit.common.auth.AuthHeaders;
import com.fundit.common.error.DependencyFailureException;
import com.fundit.fulfillment.application.funding.OrderFundingClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/**
 * order-service 내부 API 실제 구현체. order-service가 {@code GET /internal/fundings/{fundingId}}를
 * 실제로 노출하면 {@code order.integration.funding-client.mode=http}로 전환해 활성화한다
 * (payment-service {@code HttpOrderFundingClient}와 동일 패턴 — 인증은
 * {@link AuthHeaders#INTERNAL_API_KEY} 공유 시크릿을 그대로 사용, order-service의
 * {@code InternalGatewaySecretFilter}가 이 값을 검증한다는 전제).
 */
@Component
@ConditionalOnProperty(prefix = "order.integration.funding-client", name = "mode", havingValue = "http")
public class HttpOrderFundingClient implements OrderFundingClient {

    private final RestClient orderServiceRestClient;
    private final String internalApiKey;

    public HttpOrderFundingClient(RestClient orderServiceRestClient,
                                   @Value("${internal-api.key}") String internalApiKey) {
        this.orderServiceRestClient = orderServiceRestClient;
        this.internalApiKey = internalApiKey;
    }

    @Override
    public FundingSnapshot fetch(Long fundingId) {
        try {
            InternalFundingResponse response = orderServiceRestClient.get()
                    .uri("/internal/fundings/{fundingId}", fundingId)
                    .header(AuthHeaders.INTERNAL_API_KEY, internalApiKey)
                    .retrieve()
                    .body(InternalFundingResponse.class);
            if (response == null) {
                throw new DependencyFailureException(new IllegalStateException("order-service 응답 본문 없음"));
            }
            return new FundingSnapshot(response.projectId(), response.memberId());
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    private record InternalFundingResponse(Long projectId, UUID memberId) {
    }
}
