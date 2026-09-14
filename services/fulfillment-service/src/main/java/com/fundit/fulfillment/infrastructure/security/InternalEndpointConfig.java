package com.fundit.fulfillment.infrastructure.security;

import com.fundit.common.webmvc.auth.InternalEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 외부에 노출되면 안 되는 이 서비스의 엔드포인트 선언. modules:common-webmvc의
 * InternalGatewaySecretFilter가 이 빈들을 모아 X-Internal-Api-Key를 요구한다
 * (member-service {@code InternalEndpointConfig}와 동일 패턴).
 *
 * <p>게이트웨이의 라우팅 차단(404)과 짝을 이루는 두 번째 방어선이다 — platform:gateway-service
 * 라우트 설정에도 이 경로를 내부 전용으로 등록해야 한다(별도 이슈).
 */
@Configuration
public class InternalEndpointConfig {

    /** payment-service PAYMENT-006/008 판정용 배송 상태 내부 조회(FULFILLMENT-008). */
    @Bean
    public InternalEndpoint fulfillmentStatusInternalEndpoint() {
        return new InternalEndpoint("GET", "/internal/fundings/{fundingId}/fulfillment-status");
    }
}
