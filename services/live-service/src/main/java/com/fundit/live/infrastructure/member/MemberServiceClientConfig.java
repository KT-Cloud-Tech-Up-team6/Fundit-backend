package com.fundit.live.infrastructure.member;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * member-service 내부 API 호출용 RestClient. 목록 조회 경로에 끼는 호출이라 타임아웃을 짧게 둔다 —
 * member가 느려져도 라이브 목록은 판매자명만 빠진 채 나가야 한다.
 *
 * <p>base-url 기본값은 {@link com.fundit.live.infrastructure.project.ProjectServiceClientConfig}와 같은 이유로 둔다.
 */
@Configuration
public class MemberServiceClientConfig {

    @Bean
    public RestClient memberServiceRestClient(
            @Value("${live.integration.member-service.base-url:http://localhost:8082}") String baseUrl,
            @Value("${internal-api.key}") String internalApiKey,
            @Value("${live.integration.member-service.connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${live.integration.member-service.read-timeout-ms:2000}") int readTimeoutMs) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        return builder(baseUrl, internalApiKey).requestFactory(requestFactory).build();
    }

    /** 테스트가 {@code MockRestServiceServer}를 바인딩할 수 있게 Builder를 분리한다(AiClientConfig와 같은 이유). */
    static RestClient.Builder builder(String baseUrl, String internalApiKey) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                // /internal/v1/members/**는 member의 InternalGatewaySecretFilter가 이 헤더를 요구한다.
                .defaultHeader("X-Internal-Api-Key", internalApiKey);
    }
}
