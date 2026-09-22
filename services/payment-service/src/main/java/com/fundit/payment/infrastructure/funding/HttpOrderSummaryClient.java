package com.fundit.payment.infrastructure.funding;

import com.fundit.common.auth.AuthHeaders;
import com.fundit.payment.application.refund.OrderSummaryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * order-service {@code InternalFundingController}(GET /internal/orders/order-summaries) 실제 구현체.
 * {@code order.integration.funding-client.mode=http}일 때만 활성화된다({@link HttpOrderFundingClient}와
 * 같은 스위치 — 어차피 같은 order-service를 바라보는 연동이라 별도 플래그를 두지 않는다).
 */
@Component
@ConditionalOnProperty(prefix = "order.integration.funding-client", name = "mode", havingValue = "http")
public class HttpOrderSummaryClient implements OrderSummaryClient {

    private static final Logger log = LoggerFactory.getLogger(HttpOrderSummaryClient.class);

    private final RestClient orderServiceRestClient;
    private final String internalApiKey;

    public HttpOrderSummaryClient(RestClient orderServiceRestClient,
                                   @Value("${internal-api.key}") String internalApiKey) {
        this.orderServiceRestClient = orderServiceRestClient;
        this.internalApiKey = internalApiKey;
    }

    @Override
    public Map<UUID, OrderSummary> fetchBatch(List<UUID> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        try {
            List<InternalOrderSummaryResponse> response = orderServiceRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/internal/orders/order-summaries")
                            .queryParam("orderIds", orderIds).build())
                    .header(AuthHeaders.INTERNAL_API_KEY, internalApiKey)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<InternalOrderSummaryResponse>>() {
                    });
            if (response == null) {
                return Map.of();
            }
            return response.stream().collect(Collectors.toMap(InternalOrderSummaryResponse::orderId,
                    r -> new OrderSummary(r.projectTitle(), r.lineItems().stream()
                            .map(li -> new LineItem(li.rewardName(), li.quantity(), li.unitPrice(), li.options().stream()
                                    .map(o -> new LineItemOption(o.optionGroupName(), o.optionValue())).toList()))
                            .toList())));
        } catch (RestClientException e) {
            log.warn("order-service 주문 요약 배치 조회 실패(프로젝트명/상품정보 없이 진행)", e);
            return Map.of();
        }
    }

    private record InternalOrderSummaryResponse(UUID orderId, String projectTitle,
                                                  List<InternalLineItemResponse> lineItems) {
    }

    private record InternalLineItemResponse(Long rewardId, String rewardName, int quantity, long unitPrice,
                                              List<InternalLineItemOptionResponse> options) {
    }

    private record InternalLineItemOptionResponse(Long optionValueId, String optionGroupName, String optionValue) {
    }
}
