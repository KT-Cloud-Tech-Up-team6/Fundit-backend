package com.fundit.project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * scanBasePackages를 "com.fundit"으로 넓힌 이유: modules:common-webmvc의 공통 인증 설정
 * (CommonWebConfig — @LoginUser 리졸버 + InternalGatewaySecretFilter, CommonOpenApiConfig 포함)이
 * com.fundit.common.webmvc 패키지에 있어서, 기본 스캔 범위(com.fundit.project 하위)로는 잡히지 않는다.
 * 게이트웨이 라우트가 연결되면서 member-service와 동일한 방식(X-User-Id/@LoginUser CurrentUser)으로
 * 전환했다 — 예전엔 게이트웨이가 없어 CurrentMember/CurrentAdmin이 X-Account-Id를 직접 읽는
 * 임시 방식을 썼다(이제 삭제됨).
 */
@SpringBootApplication(scanBasePackages = "com.fundit")
@EnableScheduling
public class ProjectServiceApplication {

    public static void main(String[] arg) {
        SpringApplication.run(ProjectServiceApplication.class, arg);
    }
}
