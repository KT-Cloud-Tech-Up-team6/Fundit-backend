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
 * order-service 내부 API 실제 구현체. 연동 이슈에서 order-service가
 * {@code GET /internal/fundings/{fundingId}}를 실제로 노출하면
 * {@code order.integration.funding-client.mode=http}로 전환해 활성화한다.
 *
 * <p>[가정] 인증은 {@link AuthHeaders#INTERNAL_API_KEY} 공유 시크릿을 그대로 사용한다 —
 * PaymentERD.md 6장이 "내부 전용 네트워크 경로 + 서비스 토큰" 방식을 제안했는데, 레포에 이미
 * 존재하는 서비스 간 신뢰 메커니즘이 이 헤더뿐이라 재사용했다. order-service의
 * {@code InternalGatewaySecretFilter}가 이 값을 검증한다는 전제이며, 실제 값 일치는 두 서비스의
 * {@code internal-api.key} 설정이 같아야 한다[정책 확인 필요].
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
            return new FundingSnapshot(response.memberId(), response.sellerId(), response.status(),
                    response.finalAmount(), response.orderName(), response.couponIssuanceId());
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    private record InternalFundingResponse(UUID memberId, UUID sellerId, String status, long finalAmount,
                                             String orderName, Long couponIssuanceId) {
    }
}
