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

    /** fulfillment-service가 알림 팬아웃 대상(펀딩 성립 참여자)을 조회하는 내부 API. */
    @Bean
    public InternalEndpoint fundingParticipantsInternalEndpoint() {
        return new InternalEndpoint("GET", "/internal/projects/{projectId}/funding-participants");
    }
}
