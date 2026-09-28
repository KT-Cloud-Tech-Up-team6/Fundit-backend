package com.fundit.order.infrastructure.live;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * live-service 호출용 RestClient. security.md 규칙("서비스 간 동기 호출에는 반드시 타임아웃을 설정할 것")에 따라
 * connect/read 타임아웃을 명시적으로 둔다({@code FulfillmentServiceClientConfig}와 동일 패턴).
 *
 * <p>타임아웃이 기존 연동(2000/3000)보다 짧은 이유: 이 호출은 주문 생성 경로에 물린다.
 * base-url 기본값은 프로필을 안 타는 테스트 컨텍스트용이고, 실제 값은 application-{profile}.yml이 준다.
 */
@Configuration
public class LiveServiceClientConfig {

    @Bean
    public RestClient liveServiceRestClient(
            @Value("${order.integration.live-service.base-url:http://localhost:8086}") String baseUrl,
            @Value("${order.integration.live-service.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${order.integration.live-service.read-timeout-ms}") int readTimeoutMs) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
