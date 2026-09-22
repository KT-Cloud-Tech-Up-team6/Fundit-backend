package com.fundit.project.infrastructure.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;

@Configuration
public class FundingStoryAiClientConfig {

    @Bean
    RestClient fundingStoryAiRestClient(
            @Value("${funding-story.ai.base-url:http://localhost:8000}") String baseUrl,
            @Value("${funding-story.ai.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${funding-story.ai.read-timeout-ms:20000}") int readTimeoutMs,
            @Value("${funding-story.ai.require-https:false}") boolean requireHttps) {
        // service-token을 이 주소로 실어 보낸다(security.md S9) — 운영에서만 HTTPS를 강제한다.
        // dev/local은 사설망 내부 http 스텁을 그대로 쓸 수 있어야 해서 기본값은 false다.
        if (requireHttps && !"https".equalsIgnoreCase(URI.create(baseUrl).getScheme())) {
            throw new IllegalStateException("funding-story.ai.base-url은 HTTPS여야 합니다: " + baseUrl);
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
