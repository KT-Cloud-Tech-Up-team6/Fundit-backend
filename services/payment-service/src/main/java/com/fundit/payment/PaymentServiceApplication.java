package com.fundit.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * scanBasePackages를 "com.fundit"으로 넓힌 이유: modules:common-webmvc의 공통 인증 설정
 * (CommonWebConfig — @LoginUser 리졸버 + InternalGatewaySecretFilter)이 com.fundit.common.webmvc
 * 패키지에 있어서, 기본 스캔 범위(com.fundit.payment 하위)로는 잡히지 않는다.
 */
@SpringBootApplication(scanBasePackages = "com.fundit")
public class PaymentServiceApplication {

    public static void main(String[] arg) {
        SpringApplication.run(PaymentServiceApplication.class, arg);
    }
}
