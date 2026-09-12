package com.fundit.fulfillment.infrastructure.funding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * order-service 내부 API 호출용 RestClient. root CLAUDE.md "서비스 간 동기 호출에는 반드시
 * 타임아웃을 설정할 것"에 따라 connect/read 타임아웃을 명시적으로 둔다({@link HttpOrderFundingClient}
 * 활성화 시에만 필요하므로 스텁 모드에서는 빈을 만들지 않는다, payment-service
 * {@code OrderServiceClientConfig}와 동일 패턴).
 */
@Configuration
@ConditionalOnProperty(prefix = "order.integration.funding-client", name = "mode", havingValue = "http")
public class OrderServiceClientConfig {

    @Bean
    public RestClient orderServiceRestClient(
            @Value("${order.integration.base-url}") String baseUrl,
            @Value("${order.integration.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${order.integration.read-timeout-ms:3000}") int readTimeoutMs) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
