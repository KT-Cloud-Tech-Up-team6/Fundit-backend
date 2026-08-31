package com.fundit.auth.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * PasswordEncoder를 SecurityConfig에서 분리한 이유: SecurityConfig가 AccountAuthenticationProvider를
 * (그걸 통해 LoginService를) 의존하는데, LoginService는 PasswordEncoder가 필요하다. PasswordEncoder
 * 빈이 SecurityConfig 안에 있으면 SecurityConfig -> AccountAuthenticationProvider -> LoginService ->
 * SecurityConfig(PasswordEncoder) 순환 참조가 생겨 컨텍스트 로딩이 실패한다(실제로 bootRun에서 재현됨).
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
