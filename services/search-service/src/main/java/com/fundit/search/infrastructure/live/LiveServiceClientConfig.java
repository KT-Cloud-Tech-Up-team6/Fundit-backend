package com.fundit.search.infrastructure.live;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * live-service 호출용 RestClient. 루트 CLAUDE.md 규칙("서비스 간 동기 호출에는 반드시 타임아웃을
 * 설정할 것")에 따라 connect/read 타임아웃을 명시한다 — 기본값은 무한 대기라 live-service가
 * 느려지면 홈과 검색이 같이 멈춘다.
 *
 * <p>base-url 기본값을 여기 두는 이유: 이 빈은 조건부가 아니라 항상 생성되므로, 기본값이 없으면
 * 프로필을 안 타는 테스트 컨텍스트가 전부 깨진다(live-service ProjectServiceClientConfig와 동일).
 * 8086은 {@code config-convention.md}가 배정한 live-service 로컬 앱 포트다.
 */
@Configuration
public class LiveServiceClientConfig {

    @Bean
    public RestClient liveServiceRestClient(
            @Value("${search.integration.live-service.base-url:http://localhost:8086}") String baseUrl,
            @Value("${search.integration.live-service.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${search.integration.live-service.read-timeout-ms:3000}") int readTimeoutMs) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
