package com.fundit.auth.infrastructure.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * HS256 서명 키는 최소 32바이트(256비트)가 필요하다 — 짧으면 Keys.hmacShaKeyFor()가 첫 토큰 발급 시점에야 실패한다.
 * ttl이 null/0 이하면 JwtTokenProvider의 now.plus(ttl)에서 NPE가 나거나 즉시 만료되는 토큰이 발급된다
 * (Duration은 @Positive를 지원하지 않아 Hibernate Validator의 @DurationMin을 쓴다).
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    @NotBlank
    @Size(min = 32)
    private String secret;

    @NotNull
    @DurationMin(nanos = 1)
    private Duration accessTokenTtl;

    @NotNull
    @DurationMin(nanos = 1)
    private Duration refreshTokenTtl;
}
