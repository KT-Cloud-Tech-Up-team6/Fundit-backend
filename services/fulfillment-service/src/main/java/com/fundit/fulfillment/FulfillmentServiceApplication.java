package com.fundit.fulfillment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * scanBasePackages를 "com.fundit"으로 넓힌 이유: modules:common-webmvc의 공통 인증 설정
 * (CommonWebConfig — @LoginUser 리졸버 + InternalGatewaySecretFilter)이 com.fundit.common.webmvc
 * 패키지에 있어서, 기본 스캔 범위(com.fundit.fulfillment 하위)로는 잡히지 않는다.
 *
 * <p>@EnableScheduling: FULFILLMENT-004/007/010 배치(order/payment/project-service의
 * @Scheduled 배치와 동일 패턴)가 이 서비스에 붙을 예정이라 미리 켜둔다.
 */
@SpringBootApplication(scanBasePackages = "com.fundit")
@EnableScheduling
public class FulfillmentServiceApplication {

    public static void main(String[] arg) {
        SpringApplication.run(FulfillmentServiceApplication.class, arg);
    }
}
