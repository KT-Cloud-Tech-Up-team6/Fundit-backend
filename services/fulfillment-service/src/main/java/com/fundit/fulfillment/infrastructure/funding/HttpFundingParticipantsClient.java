package com.fundit.fulfillment.infrastructure.funding;

import com.fundit.common.auth.AuthHeaders;
import com.fundit.common.error.DependencyFailureException;
import com.fundit.fulfillment.application.funding.FundingParticipantsClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.UUID;

/**
 * order-service 내부 API 실제 구현체 — {@link HttpOrderFundingClient}와 같은
 * {@code order.integration.funding-client.mode=http} 플래그로 함께 활성화된다(같은 RestClient 재사용).
 */
@Component
@ConditionalOnProperty(prefix = "order.integration.funding-client", name = "mode", havingValue = "http")
public class HttpFundingParticipantsClient implements FundingParticipantsClient {

    private final RestClient orderServiceRestClient;
    private final String internalApiKey;

    public HttpFundingParticipantsClient(RestClient orderServiceRestClient,
                                          @Value("${internal-api.key}") String internalApiKey) {
        this.orderServiceRestClient = orderServiceRestClient;
        this.internalApiKey = internalApiKey;
    }

    @Override
    public List<UUID> listParticipantMemberIds(UUID projectId) {
        try {
            InternalFundingParticipantsResponse response = orderServiceRestClient.get()
                    .uri("/internal/projects/{projectId}/funding-participants", projectId)
                    .header(AuthHeaders.INTERNAL_API_KEY, internalApiKey)
                    .retrieve()
                    .body(InternalFundingParticipantsResponse.class);
            if (response == null) {
                throw new DependencyFailureException(new IllegalStateException("order-service 응답 본문 없음"));
            }
            return response.memberIds();
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    private record InternalFundingParticipantsResponse(List<UUID> memberIds) {
    }
}
