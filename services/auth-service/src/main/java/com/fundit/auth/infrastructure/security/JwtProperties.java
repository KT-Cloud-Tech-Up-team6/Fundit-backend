package com.fundit.auth.infrastructure.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * RS256 서명에 쓰는 RSA 개인키와 토큰 수명.
 *
 * <p>{@code privateKey}는 PKCS#8 DER을 base64로 인코딩한 <b>한 줄 문자열</b>이다 —
 * PEM 원문은 여러 줄이라 yml 플레이스홀더/환경변수에서 개행 때문에 깨진다.
 * 공개키는 별도로 받지 않고 개인키에서 유도하므로, 배포 환경이 관리할 시크릿은 이것 하나다.
 *
 * <p>ttl이 null/0 이하면 JwtTokenProvider의 now.plus(ttl)에서 NPE가 나거나 즉시 만료되는 토큰이
 * 발급된다(Duration은 @Positive를 지원하지 않아 Hibernate Validator의 @DurationMin을 쓴다).
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    @NotBlank
    private String privateKey;

    @NotNull
    @DurationMin(nanos = 1)
    private Duration accessTokenTtl;

    @NotNull
    @DurationMin(nanos = 1)
    private Duration refreshTokenTtl;
}
