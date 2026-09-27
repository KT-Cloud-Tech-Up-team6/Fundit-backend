package com.fundit.order.infrastructure.refund;

import com.fundit.common.auth.AuthHeaders;
import com.fundit.order.application.refund.RefundStatusClient;
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

/** payment-service {@code InternalRefundController} 실제 구현체. */
@Component
public class PaymentServiceRefundStatusClient implements RefundStatusClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceRefundStatusClient.class);

    private final RestClient paymentServiceRestClient;
    private final String internalApiKey;

    public PaymentServiceRefundStatusClient(RestClient paymentServiceRestClient,
                                             @Value("${internal-api.key}") String internalApiKey) {
        this.paymentServiceRestClient = paymentServiceRestClient;
        this.internalApiKey = internalApiKey;
    }

    @Override
    public Map<UUID, List<RefundStatus>> fetchBatch(List<UUID> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        try {
            List<InternalRefundStatusResponse> response = paymentServiceRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/internal/refunds/statuses")
                            .queryParam("fundingIds", orderIds).build())
                    .header(AuthHeaders.INTERNAL_API_KEY, internalApiKey)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<InternalRefundStatusResponse>>() {
                    });
            if (response == null) {
                return Map.of();
            }
            return response.stream().collect(Collectors.groupingBy(InternalRefundStatusResponse::fundingId,
                    Collectors.mapping(r -> new RefundStatus(r.refundId(), r.triggerType(), r.status(),
                            r.requestedAt()), Collectors.toList())));
        } catch (RestClientException e) {
            log.warn("payment-service 신청 이력 배치 조회 실패(신청 여부 없이 진행)", e);
            return Map.of();
        }
    }

    private record InternalRefundStatusResponse(UUID fundingId, Long refundId, String triggerType, String status,
                                                  Instant requestedAt) {
    }
}
