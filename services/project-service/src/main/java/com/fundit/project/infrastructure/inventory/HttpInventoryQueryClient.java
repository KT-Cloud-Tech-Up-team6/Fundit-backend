package com.fundit.project.infrastructure.inventory;

import com.fundit.common.auth.AuthHeaders;
import com.fundit.project.application.reward.InventoryQueryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * order-service {@code GET /api/v1/inventories/{rewardId}}(내부 전용, X-Internal-Api-Key 필요)
 * 실제 구현체. 재고 조회는 리워드 상세 화면의 soldOut/remainingStock 표시용 best-effort 데이터라,
 * 조회 실패 시 {@link com.fundit.common.error.DependencyFailureException}을 던져 리워드 상세
 * 조회 전체를 막지 않고 {@link Optional#empty()}로 degrade한다
 * ({@code ProjectServiceProjectOwnershipClient}처럼 실패를 전파해야 하는 소유권 검증과는 다르다).
 */
@Component
@ConditionalOnProperty(prefix = "order.integration.inventory-client", name = "mode", havingValue = "http")
public class HttpInventoryQueryClient implements InventoryQueryClient {

    private static final Logger log = LoggerFactory.getLogger(HttpInventoryQueryClient.class);

    private final RestClient orderServiceRestClient;
    private final String internalApiKey;

    public HttpInventoryQueryClient(RestClient orderServiceRestClient,
                                     @Value("${internal-api.key}") String internalApiKey) {
        this.orderServiceRestClient = orderServiceRestClient;
        this.internalApiKey = internalApiKey;
    }

    @Override
    public Optional<Integer> getRemainingStock(Long rewardId) {
        try {
            InventoryResponse response = orderServiceRestClient.get()
                    .uri("/api/v1/inventories/{rewardId}", rewardId)
                    .header(AuthHeaders.INTERNAL_API_KEY, internalApiKey)
                    .retrieve()
                    .body(InventoryResponse.class);
            return response == null ? Optional.empty() : Optional.ofNullable(response.remainingStock());
        } catch (RestClientException e) {
            log.warn("order-service 재고 조회 실패 — remainingStock을 빈 값으로 처리합니다. rewardId={}", rewardId, e);
            return Optional.empty();
        }
    }

    private record InventoryResponse(Long rewardId, Integer remainingStock) {
    }
}
