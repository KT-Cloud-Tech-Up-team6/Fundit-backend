package com.fundit.fulfillment.infrastructure.project;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * project-service 내부 API 호출용 RestClient. root CLAUDE.md "서비스 간 동기 호출에는 반드시
 * 타임아웃을 설정할 것"에 따라 connect/read 타임아웃을 명시적으로 둔다({@link HttpProjectOwnershipClient}
 * 활성화 시에만 필요하므로 스텁 모드에서는 빈을 만들지 않는다).
 */
@Configuration
@ConditionalOnProperty(prefix = "project.integration.ownership-client", name = "mode", havingValue = "http")
public class ProjectServiceClientConfig {

    @Bean
    public RestClient projectServiceRestClient(
            @Value("${project.integration.base-url}") String baseUrl,
            @Value("${project.integration.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${project.integration.read-timeout-ms:3000}") int readTimeoutMs) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
