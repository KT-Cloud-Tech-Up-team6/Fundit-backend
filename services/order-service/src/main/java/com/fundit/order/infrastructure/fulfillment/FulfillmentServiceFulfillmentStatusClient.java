package com.fundit.order.infrastructure.fulfillment;

import com.fundit.common.auth.AuthHeaders;
import com.fundit.common.error.DependencyFailureException;
import com.fundit.order.application.fulfillment.FulfillmentStatusClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * fulfillment-service {@code InternalFulfillmentController}(FULFILLMENT-008) 실제 구현체.
 * payment-service {@code HttpShippingStatusClient}와 동일한 엔드포인트를 쓴다.
 */
@Component
public class FulfillmentServiceFulfillmentStatusClient implements FulfillmentStatusClient {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentServiceFulfillmentStatusClient.class);

    private final RestClient fulfillmentServiceRestClient;
    private final String internalApiKey;

    public FulfillmentServiceFulfillmentStatusClient(RestClient fulfillmentServiceRestClient,
                                                       @Value("${internal-api.key}") String internalApiKey) {
        this.fulfillmentServiceRestClient = fulfillmentServiceRestClient;
        this.internalApiKey = internalApiKey;
    }

    @Override
    public FulfillmentStatus fetch(UUID orderId) {
        try {
            InternalFulfillmentStatusResponse response = fulfillmentServiceRestClient.get()
                    .uri("/internal/fundings/{fundingId}/fulfillment-status", orderId)
                    .header(AuthHeaders.INTERNAL_API_KEY, internalApiKey)
                    .retrieve()
                    .body(InternalFulfillmentStatusResponse.class);
            if (response == null) {
                throw new DependencyFailureException(new IllegalStateException("fulfillment-service 응답 본문 없음"));
            }
            return new FulfillmentStatus(response.isAlreadyShipped(), response.deliveredAt() != null);
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    /** 목록 화면 배지/액션은 부가 정보라 조회 실패해도 목록 자체는 내려가야 해서 예외를 던지지 않는다. */
    @Override
    public Map<UUID, FulfillmentStatus> fetchBatch(List<UUID> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        try {
            List<InternalBatchStatusResponse> response = fulfillmentServiceRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/internal/fundings/fulfillment-statuses")
                            .queryParam("fundingIds", orderIds).build())
                    .header(AuthHeaders.INTERNAL_API_KEY, internalApiKey)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<InternalBatchStatusResponse>>() {
                    });
            if (response == null) {
                return Map.of();
            }
            return response.stream().collect(Collectors.toMap(InternalBatchStatusResponse::fundingId,
                    r -> new FulfillmentStatus(r.isAlreadyShipped(), r.deliveredAt() != null)));
        } catch (RestClientException e) {
            log.warn("fulfillment-service 배치 조회 실패(가능 액션/배지 없이 진행)", e);
            return Map.of();
        }
    }

    private record InternalFulfillmentStatusResponse(boolean isAlreadyShipped, boolean isDelayed,
                                                       Instant deliveredAt, Instant receiptConfirmedAt) {
    }

    private record InternalBatchStatusResponse(UUID fundingId, boolean isAlreadyShipped, Instant deliveredAt) {
    }
}
