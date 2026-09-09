package com.fundit.payment.infrastructure.toss;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 토스페이먼츠 API 호출용 RestClient. security.md 규칙(외부 API 연동 S7 — 시크릿은 코드에
 * 하드코딩하지 않고 설정/환경변수로 분리 / 서비스 간 동기 호출 타임아웃 필수)을 따른다.
 *
 * <p><b>반드시 "결제위젯 연동 키" 페어의 시크릿 키를 써야 한다</b> — "API 개별연동 키"와는
 * 다른 키 페어다(PaymentERD.md 1장, payment-service CLAUDE.md "Toss 연동" 참고). Basic 인증은
 * {@code secretKey + ":"}를 Base64 인코딩한 값을 Authorization 헤더에 싣는다(토스 공식 가이드
 * "인증 및 기타 헤더 설정" 기준).
 */
@Configuration
public class TossClientConfig {

    @Bean
    public RestClient tossPaymentsRestClient(
            @Value("${toss.payments.secret-key}") String secretKey,
            @Value("${toss.payments.base-url:https://api.tosspayments.com}") String baseUrl,
            @Value("${toss.payments.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${toss.payments.read-timeout-ms:5000}") int readTimeoutMs) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        String encodedSecret = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedSecret)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
