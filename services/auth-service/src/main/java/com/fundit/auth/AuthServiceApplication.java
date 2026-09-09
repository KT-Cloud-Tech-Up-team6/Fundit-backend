package com.fundit.auth;

import com.fundit.common.webmvc.openapi.CommonOpenApiConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * member-service처럼 scanBasePackages를 "com.fundit"으로 넓히지 않고 필요한 설정만 @Import한다 —
 * 넓히면 CommonWebConfig(@LoginUser 리졸버 + InternalGatewaySecretFilter)까지 딸려오는데,
 * 이 서비스는 게이트웨이가 넣는 X-User-Id를 쓰지 않고(Security 필터가 JWT를 직접 검증한다)
 * 내부 전용 엔드포인트도 없어서 전부 죽은 무게다.
 */
@SpringBootApplication
@Import(CommonOpenApiConfig.class)
public class AuthServiceApplication {

    public static void main(String[] arg) {
        SpringApplication.run(AuthServiceApplication.class, arg);
    }
}
