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
 * order-service 내부 API 실제 구현체. 주 경로는 {@code GET /internal/orders/{orderId}}(UUID)이고,
 * 레거시 Long PK 조회는 {@code GET /internal/fundings/{fundingId}}를 쓴다.
 * 인증은 {@link AuthHeaders#INTERNAL_API_KEY} 공유 시크릿을 그대로 사용한다.
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
    public FundingSnapshot fetch(UUID orderId) {
        return fetchUri("/internal/orders/{orderId}", orderId);
    }

    @Override
    public FundingSnapshot fetchByInternalId(Long fundingId) {
        return fetchUri("/internal/fundings/{fundingId}", fundingId);
    }

    private FundingSnapshot fetchUri(String uri, Object id) {
        try {
            InternalFundingResponse response = orderServiceRestClient.get()
                    .uri(uri, id)
                    .header(AuthHeaders.INTERNAL_API_KEY, internalApiKey)
                    .retrieve()
                    .body(InternalFundingResponse.class);
            if (response == null) {
                throw new DependencyFailureException(new IllegalStateException("order-service 응답 본문 없음"));
            }
            return new FundingSnapshot(response.projectId(), response.memberId(), response.fundingPublicId());
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    private record InternalFundingResponse(Long fundingId, UUID projectId, UUID memberId, UUID fundingPublicId) {
    }
}
