package com.fundit.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * scanBasePackages를 "com.fundit"으로 넓힌 이유: modules:common-webmvc의 공통 인증 설정
 * (CommonWebConfig — @LoginUser 리졸버 + InternalGatewaySecretFilter)이 com.fundit.common.webmvc
 * 패키지에 있어서, 기본 스캔 범위(com.fundit.payment 하위)로는 잡히지 않는다.
 *
 * <p>{@code @EnableScheduling}: 아웃박스 워커(PaymentEventOutboxWorker, PAYMENT-016)와
 * 정산 배치(SettlementScheduleWorker/SettlementPayoutScheduler, PAYMENT-013~015)가
 * {@code @Scheduled}로 동작한다(order-service OrderServiceApplication과 동일 패턴).
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.fundit")
public class PaymentServiceApplication {

    public static void main(String[] arg) {
            SpringApplication.run(PaymentServiceApplication.class, arg);
    }
}
