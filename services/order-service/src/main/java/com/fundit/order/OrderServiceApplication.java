package com.fundit.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling: 아웃박스 워커(FundingEventOutboxWorker)와 배치(결제 만료 ORDER-013,
// 목표 달성 판정 ORDER-006)가 전부 @Scheduled 기반이라 필요하다.
// scanBasePackages="com.fundit": 기본 스캔 범위는 이 클래스 패키지(com.fundit.order) 하위뿐이라
// modules:common-webmvc의 CommonWebConfig(com.fundit.common.webmvc.auth 패키지)가 빠진다 —
// payment/member-service와 동일 이유.
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.fundit")
public class OrderServiceApplication {

    public static void main(String[] arg) {
        SpringApplication.run(OrderServiceApplication.class, arg);
    }
}
