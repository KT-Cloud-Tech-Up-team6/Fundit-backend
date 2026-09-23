package com.fundit.live.infrastructure.ai;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 서버 호출용 RestClient 2개. {@code submitComments}(댓글 배치)만 별도 타임아웃을 쓴다 —
 * 배치 50건이면 댓글당 평균 1초 순차 처리로 최대 1분까지 걸릴 수 있다고 AI팀이 명시했다.
 * 나머지 엔드포인트에 그 타임아웃을 그대로 쓰면 진짜 장애(AI 서버 다운)를 1분 넘게 기다리게 된다.
 *
 * <p>{@code live.ai.mode=http}일 때만 뜬다 — 스텁 모드에서는 만들 이유가 없다.
 */
@Configuration
@ConditionalOnProperty(name = "live.ai.mode", havingValue = "http")
public class AiClientConfig {

    @Bean
    @Qualifier("aiRestClient")
    public RestClient aiRestClient(
            @Value("${live.ai.base-url}") String baseUrl,
            @Value("${live.ai.token}") String token,
            @Value("${live.ai.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${live.ai.read-timeout-ms:3000}") int readTimeoutMs) {
        return build(baseUrl, token, connectTimeoutMs, readTimeoutMs);
    }

    @Bean
    @Qualifier("aiCommentsRestClient")
    public RestClient aiCommentsRestClient(
            @Value("${live.ai.base-url}") String baseUrl,
            @Value("${live.ai.token}") String token,
            @Value("${live.ai.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${live.ai.comments-read-timeout-ms:65000}") int commentsReadTimeoutMs) {
        return build(baseUrl, token, connectTimeoutMs, commentsReadTimeoutMs);
    }

    /**
     * 하이라이트는 Q&A 코파일럿과 같은 서버다(큐시트와 다름) — base-url·토큰은 공유하고
     * 타임아웃만 분리한다. 결과는 콜백(push)으로 오므로 이 호출은 202 접수 응답만 기다리면
     * 되지만, 기본 3초는 접수 자체도 촉박할 수 있어 여유를 둔다(AI팀 요청).
     */
    @Bean
    @Qualifier("aiHighlightsRestClient")
    public RestClient aiHighlightsRestClient(
            @Value("${live.ai.base-url}") String baseUrl,
            @Value("${live.ai.token}") String token,
            @Value("${live.ai.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${live.ai.highlights-read-timeout-ms:10000}") int highlightsReadTimeoutMs) {
        return build(baseUrl, token, connectTimeoutMs, highlightsReadTimeoutMs);
    }

    /**
     * 큐시트는 Q&A 코파일럿과 **다른 AI 서버**다(별도 레포·팀, 2026-09-22 협의 확정) — base-url·
     * 토큰을 공유하면 안 된다. 읽기 타임아웃 기본 200초는 실측(평균 86.5초·최대 122.1초) +
     * 여유분이다 — "3분 이상 잡아달라"는 합의를 넉넉히 충족한다.
     */
    @Bean
    @Qualifier("cuesheetAiRestClient")
    public RestClient cuesheetAiRestClient(
            @Value("${live.cuesheet-ai.base-url}") String baseUrl,
            @Value("${live.cuesheet-ai.token}") String token,
            @Value("${live.cuesheet-ai.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${live.cuesheet-ai.read-timeout-ms:200000}") int readTimeoutMs) {
        return build(baseUrl, token, connectTimeoutMs, readTimeoutMs);
    }

    private static RestClient build(String baseUrl, String token, int connectTimeoutMs, int readTimeoutMs) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        return builder(baseUrl, token).requestFactory(requestFactory).build();
    }

    /**
     * 타임아웃을 뺀 나머지 설정(base-url·인증 헤더·snake_case 컨버터)만 조립한다.
     * 테스트가 {@code MockRestServiceServer}를 바인딩하려면 완성된 {@link RestClient}가 아니라
     * 이 {@code Builder}가 필요해 분리해뒀다.
     */
    static RestClient.Builder builder(String baseUrl, String token) {
        var builder = RestClient.builder()
                .baseUrl(baseUrl)
                // AI 계약이 전부 snake_case다(product_name, at_ms 등). AiClient의 record는
                // 전부 camelCase로 두고 여기서 한 번만 변환한다 — 필드마다 @JsonProperty를
                // 붙이면 실계약이 필드를 늘릴 때마다 우리 쪽도 매번 고쳐야 한다.
                .configureMessageConverters(clientBuilder -> clientBuilder
                        .withJsonConverter(new JacksonJsonHttpMessageConverter(JsonMapper.builder()
                                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                                // 소비자는 모르는 필드를 무시해야 한다(event-convention.md 6번과 같은
                                // 원칙) — AI가 필드를 추가해도 우리가 매번 배포하지 않는다.
                                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                // 위 플래그는 모르는 "필드"만 막아준다. 모르는 enum "값"은 그대로
                                // 예외라, AI가 Grounding/HandledBy/AnsweredBy에 값을 하나 추가하면
                                // 배치 응답 전체가 깨져 그 세션 채팅이 AI로 못 간다. 해당 필드만
                                // null로 떨어뜨리고 나머지는 살린다.
                                .enable(EnumFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
                                .build())));
        // 서버 env API_TOKEN 미설정(개발 모드)이면 인증을 안 본다지만, 우리 쪽 값이 비어 있는
        // 채로 배포하는 실수를 막으려면 여기서 걸러야 한다 — 빈 문자열이면 헤더 자체를 안 붙인다.
        if (token != null && !token.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return builder;
    }
}
