package com.fundit.project.infrastructure.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class FundingStoryAiClientConfig {

    @Bean
    RestClient fundingStoryAiRestClient(
            @Value("${funding-story.ai.base-url:http://localhost:8000}") String baseUrl,
            @Value("${funding-story.ai.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${funding-story.ai.read-timeout-ms:20000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
