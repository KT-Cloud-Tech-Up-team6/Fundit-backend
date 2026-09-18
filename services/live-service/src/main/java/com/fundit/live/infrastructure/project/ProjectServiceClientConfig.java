package com.fundit.live.infrastructure.project;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * project-service 호출용 RestClient. 루트 CLAUDE.md 규칙("서비스 간 동기 호출에는 반드시
 * 타임아웃을 설정할 것")에 따라 connect/read 타임아웃을 명시한다 —
 * 기본값은 무한 대기라 project-service가 느려지면 LIVE 생성이 같이 멈춘다.
 *
 * <p>base-url 기본값을 여기 두는 이유: 이 빈은 조건부가 아니라 항상 생성되므로,
 * 기본값이 없으면 프로필을 안 타는 테스트 컨텍스트가 전부 깨진다(order-service와 동일).
 */
@Configuration
public class ProjectServiceClientConfig {

    @Bean
    public RestClient projectServiceRestClient(
            @Value("${live.integration.project-service.base-url:http://localhost:8083}") String baseUrl,
            @Value("${live.integration.project-service.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${live.integration.project-service.read-timeout-ms:3000}") int readTimeoutMs) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
