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
            @Value("${order.integration.project-service.base-url}") String baseUrl,
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
