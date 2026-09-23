package com.fundit.live.infrastructure.security;

import com.fundit.common.webmvc.auth.InternalEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 외부에 노출되면 안 되는 엔드포인트 선언. {@code InternalGatewaySecretFilter}가 이 빈들을 모아
 * {@code X-Internal-Api-Key}를 요구한다(project/fulfillment-service와 동일 패턴).
 *
 * <p>게이트웨이 라우팅에서도 제외해야 한다 — 게이트웨이는 <b>모든</b> 프록시 요청에 내부 키를
 * 주입하므로, 라우트가 열려 있으면 외부 클라이언트가 게이트웨이를 통해 이 필터를 그냥 통과한다.
 */
@Configuration
public class InternalEndpointConfig {

    /** IVS Chat Logging(Firehose)이 채팅을 적재하는 경로. 열려 있으면 임의 채팅 주입이 가능하다. */
    @Bean
    public InternalEndpoint chatIngestEndpoint() {
        return new InternalEndpoint("POST", "/internal/v1/lives/chat/messages");
    }

    /** order-service가 라이브 쿠폰 발급 전 "방송 진행 중"을 확인하는 경로. */
    @Bean
    public InternalEndpoint liveStatusEndpoint() {
        return new InternalEndpoint("GET", "/internal/v1/lives/{liveId}/status");
    }

    /** order-service가 주문 생성 시 프로젝트의 진행 중 방송을 찾는 경로. */
    @Bean
    public InternalEndpoint liveActiveStatusByProjectEndpoint() {
        return new InternalEndpoint("GET", "/internal/v1/lives/by-project/{projectId}/active-status");
    }

    /** order-service가 라이브 쿠폰 클레임 시 세션 상태를 확인하는 경로(내부 세션 PK 기준). */
    @Bean
    public InternalEndpoint liveStatusBySessionEndpoint() {
        return new InternalEndpoint("GET", "/internal/v1/lives/sessions/{sessionId}/status");
    }

    /** AI가 하이라이트 생성 결과를 밀어주는 경로. */
    @Bean
    public InternalEndpoint highlightCallbackEndpoint() {
        return new InternalEndpoint("POST", "/internal/v1/lives/{liveId}/highlights");
    }
}
