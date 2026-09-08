package com.fundit.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 도메인 서비스들과 달리 {@code scanBasePackages = "com.fundit"}를 쓰지 않는다 —
 * modules:common-webmvc의 공통 설정(ArgumentResolver/Filter)은 Servlet 전용이라
 * 리액티브 스택인 게이트웨이가 스캔하면 안 된다. modules:common에서 쓰는 건
 * 빈이 아니라 순수 상수/레코드(AuthHeaders, ErrorResponse)뿐이라 스캔 범위를 넓힐 필요가 없다.
 */
@SpringBootApplication
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
