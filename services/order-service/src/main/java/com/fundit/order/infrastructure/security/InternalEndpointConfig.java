package com.fundit.order.infrastructure.security;

import com.fundit.common.webmvc.auth.InternalEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 외부에 노출되면 안 되는 이 서비스의 엔드포인트 선언. modules:common-webmvc의
 * InternalGatewaySecretFilter가 이 빈들을 모아 X-Internal-Api-Key를 요구한다
 * (fulfillment-service {@code InternalEndpointConfig}와 동일 패턴).
 */
@Configuration
public class InternalEndpointConfig {

    /** payment/fulfillment-service가 펀딩 스냅샷(projectId/memberId 등)을 조회하는 내부 API. */
    @Bean
    public InternalEndpoint fundingInternalEndpoint() {
        return new InternalEndpoint("GET", "/internal/fundings/{fundingId}");
    }

    /** cross-service ID 통일(#69) — 외부 노출 orderId(UUID)로 펀딩 스냅샷을 조회하는 내부 API. */
    @Bean
    public InternalEndpoint fundingByOrderIdInternalEndpoint() {
        return new InternalEndpoint("GET", "/internal/orders/{orderId}");
    }

    /** fulfillment-service가 알림 팬아웃 대상(펀딩 성립 참여자)을 조회하는 내부 API. */
    @Bean
    public InternalEndpoint fundingParticipantsInternalEndpoint() {
        return new InternalEndpoint("GET", "/internal/projects/{projectId}/funding-participants");
    }

    /**
     * project-service가 리워드 잔여재고를 동기 조회하는 내부 API. 게이트웨이 라우트에는 원래부터
     * 없었고(1차 방어선, {@code application.yml} 참고), 여기 등록해 X-Internal-Api-Key 검증도
     * 걸어둔다(2차 방어선) — 다른 내부 엔드포인트와 동일한 이중 방어.
     */
    @Bean
    public InternalEndpoint inventoryInternalEndpoint() {
        return new InternalEndpoint("GET", "/api/v1/inventories/{rewardId}");
    }
}
