package com.fundit.common.webmvc.auth;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 게이트웨이 뒤에 서는 Servlet 기반 서비스가 공통으로 쓰는 인증 플러밍 설정.
 *
 * <p>이 설정을 자동으로 잡으려면 서비스 메인 클래스에
 * {@code @SpringBootApplication(scanBasePackages = "com.fundit")}가 필요하다 —
 * 기본 스캔 범위는 메인 클래스 패키지 하위뿐이라 {@code com.fundit.common.webmvc.*}가 빠진다.
 *
 * <p>{@link InternalGatewaySecretFilter}를 먼저 태우고 그 다음에
 * {@link LoginUserArgumentResolver}가 헤더를 읽는 순서가 중요하다 — 리졸버는 헤더를 그대로
 * 믿기 때문에, 앞단에서 출처 검증이 끝나 있어야 한다.
 */
@Configuration
public class CommonWebConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new LoginUserArgumentResolver());
    }

    /**
     * 내부 전용 엔드포인트 목록은 서비스가 {@link InternalEndpoint} 빈으로 선언한다.
     * yml 설정으로 두지 않는 이유: 이건 환경별로 달라지는 값이 아니라 그 서비스의 API 형태이고,
     * 설정으로 두면 특정 프로필(특히 운영)에서 빠뜨렸을 때 조용히 무방비가 된다.
     * 선언한 빈이 없으면 빈 목록 — 내부 전용 엔드포인트가 없는 서비스도 있다.
     */
    @Bean
    public FilterRegistrationBean<InternalGatewaySecretFilter> internalGatewaySecretFilterRegistration(
            @Value("${internal-api.key}") String internalApiKey,
            ObjectProvider<InternalEndpoint> internalEndpoints,
            ObjectMapper objectMapper) {

        var registration = new FilterRegistrationBean<>(new InternalGatewaySecretFilter(
                internalApiKey, internalEndpoints.stream().toList(), objectMapper));
        // 신원을 주장하는 요청은 어느 경로로 오든 걸러야 하므로 전 경로에 건다.
        // 실제 검증 여부는 필터 안에서 요청별로 판단한다(공개 API는 그냥 통과).
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
