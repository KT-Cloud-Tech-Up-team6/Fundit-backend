package com.fundit.payment.infrastructure.funding;

import com.fundit.common.auth.AuthHeaders;
import com.fundit.common.error.DependencyFailureException;
import com.fundit.payment.application.funding.OrderFundingClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/**
 * order-service 내부 API 실제 구현체.
 * {@code order.integration.funding-client.mode=http}로 전환해 활성화한다.
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
        return get("/internal/orders/{orderId}", orderId);
    }

    @Override
    public FundingSnapshot fetchByInternalId(Long fundingId) {
        return get("/internal/fundings/{fundingId}", fundingId);
    }

    private FundingSnapshot get(String path, Object id) {
        try {
            InternalFundingResponse response = orderServiceRestClient.get()
                    .uri(path, id)
                    .header(AuthHeaders.INTERNAL_API_KEY, internalApiKey)
                    .retrieve()
                    .body(InternalFundingResponse.class);
            if (response == null) {
                throw new DependencyFailureException(new IllegalStateException("order-service 응답 본문 없음"));
            }
            return new FundingSnapshot(response.memberId(), response.sellerId(), response.status(),
                    response.finalAmount(), response.orderName(), response.couponIssuanceId(),
                    response.fundingPublicId());
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    private record InternalFundingResponse(UUID memberId, UUID sellerId, String status, long finalAmount,
                                             String orderName, Long couponIssuanceId, UUID fundingPublicId) {
    }
}
