package com.fundit.order.infrastructure.catalog;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * project-service 호출용 RestClient. security.md 규칙("서비스 간 동기 호출에는 반드시
 * 타임아웃을 설정할 것")에 따라 connect/read 타임아웃을 명시적으로 둔다.
 */
@Configuration
public class ProjectServiceClientConfig {

    @Bean
    public RestClient projectServiceRestClient(
            // 기본값(local 프로필 편의용 + @SpringBootTest가 별도 프로필 없이 컨텍스트를 띄울 때의
            // 안전망)은 여기 두고, dev/prod는 application-{profile}.yml이 환경변수로 덮어쓴다 —
            // 이 빈은 조건부(stub/http 스위치)가 아니라 항상 생성되므로 기본값이 없으면
            // 프로필을 안 타는 모든 테스트 컨텍스트가 PlaceholderResolutionException으로 깨진다.
            @Value("${order.integration.project-service.base-url:http://localhost:8083}") String baseUrl,
            @Value("${order.integration.project-service.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${order.integration.project-service.read-timeout-ms}") int readTimeoutMs) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
