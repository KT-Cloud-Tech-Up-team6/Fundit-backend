package com.fundit.auth.infrastructure.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** HS256 서명 키는 최소 32바이트(256비트)가 필요하다 — 짧으면 Keys.hmacShaKeyFor()가 첫 토큰 발급 시점에야 실패한다. */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    @NotBlank
    @Size(min = 32)
    private String secret;
    private Duration accessTokenTtl;
    private Duration refreshTokenTtl;
}
