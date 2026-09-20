package com.fundit.payment.infrastructure.settlement;

import com.fundit.common.auth.AuthHeaders;
import com.fundit.common.error.DependencyFailureException;
import com.fundit.payment.application.settlement.OrderSettlementAggregateClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * order-service {@code InternalFundingController}(GET /internal/fundings/{fundingId}/settlement-aggregate)
 * 실제 구현체. {@code order.integration.funding-client.mode=http}일 때만 활성화된다
 * ({@link com.fundit.payment.infrastructure.funding.HttpOrderFundingClient}와 같은 스위치 —
 * 어차피 같은 order-service를 바라보는 연동이라 별도 플래그를 두지 않는다).
 */
@Component
@ConditionalOnProperty(prefix = "order.integration.funding-client", name = "mode", havingValue = "http")
public class HttpOrderSettlementAggregateClient implements OrderSettlementAggregateClient {

    private final RestClient orderServiceRestClient;
    private final String internalApiKey;

    public HttpOrderSettlementAggregateClient(RestClient orderServiceRestClient,
                                               @Value("${internal-api.key}") String internalApiKey) {
        this.orderServiceRestClient = orderServiceRestClient;
        this.internalApiKey = internalApiKey;
    }

    @Override
    public List<LineItemAggregate> fetchLineItems(Long fundingId) {
        return fetch(fundingId).lineItems().stream()
                .map(li -> new LineItemAggregate(li.rewardId(), li.rewardName(), li.optionName(), li.quantity(), li.amount()))
                .toList();
    }

    @Override
    public long fetchMakerCouponDeductionAmount(Long fundingId) {
        return fetch(fundingId).makerCouponDeductionAmount();
    }

    private InternalSettlementAggregateResponse fetch(Long fundingId) {
        try {
            InternalSettlementAggregateResponse response = orderServiceRestClient.get()
                    .uri("/internal/fundings/{fundingId}/settlement-aggregate", fundingId)
                    .header(AuthHeaders.INTERNAL_API_KEY, internalApiKey)
                    .retrieve()
                    .body(InternalSettlementAggregateResponse.class);
            if (response == null) {
                throw new DependencyFailureException(new IllegalStateException("order-service 응답 본문 없음"));
            }
            return response;
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    private record InternalSettlementAggregateResponse(List<InternalLineItem> lineItems, long makerCouponDeductionAmount) {
    }

    private record InternalLineItem(Long rewardId, String rewardName, String optionName, int quantity, long amount) {
    }
}
