package com.fundit.member;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * scanBasePackages를 "com.fundit"으로 넓힌 이유: modules:common-webmvc의 공통 인증 설정
 * (CommonWebConfig — @LoginUser 리졸버 + InternalGatewaySecretFilter)이 com.fundit.common.webmvc
 * 패키지에 있어서, 기본 스캔 범위(com.fundit.member 하위)로는 잡히지 않는다.
 *
 * <p>@EnableScheduling: 찜 이벤트 아웃박스 워커(WishEventOutboxWorker, MEMBER-005)가
 * @Scheduled로 돈다. 이게 없으면 예외 없이 조용히 안 돌고 이벤트만 영영 미발행으로 쌓인다.
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.fundit")
public class MemberServiceApplication {

    public static void main(String[] arg) {
        SpringApplication.run(MemberServiceApplication.class, arg);
    }
}
