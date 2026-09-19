package com.fundit.project.infrastructure.security;

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

    /** fulfillment-service가 판매자 소유권(sellerId)을 조회하는 내부 API. */
    @Bean
    public InternalEndpoint projectInternalEndpoint() {
        return new InternalEndpoint("GET", "/internal/projects/{projectId}");
    }

    /** order-service 주문 목록(V03)이 창작자명·썸네일 배치 조회에 쓰는 내부 API. */
    @Bean
    public InternalEndpoint projectSummariesInternalEndpoint() {
        return new InternalEndpoint("GET", "/internal/projects/summaries");
    }
}
