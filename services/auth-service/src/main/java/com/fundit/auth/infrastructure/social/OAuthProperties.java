package com.fundit.auth.infrastructure.social;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 소셜 제공자 설정. {@code @Value}가 아니라 {@code @ConfigurationProperties}로 읽는 이유:
 * {@code @Value}는 값이 없으면 컨텍스트 기동 자체가 실패해서, 이 값을 쓸 일 없는
 * {@code @SpringBootTest}까지 전부 깨진다(이 레포에서 {@code internal-api.key} 추가 때 실제로 겪었다).
 * {@code PortOneProperties}가 같은 이유로 같은 방식이다.
 *
 * <p>{@code redirectUri}는 클라이언트가 보내는 값이 아니라 서버 설정으로 고정한다 —
 * 외부 요청 URL을 사용자 입력으로 받지 않는다(security.md S7).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "oauth")
public class OAuthProperties {

    private Provider kakao = new Provider();
    private Provider google = new Provider();

    @Getter
    @Setter
    public static class Provider {
        private String clientId;
        private String clientSecret;
        private String redirectUri;
        private String tokenUri;
        private String userInfoUri;
    }
}
