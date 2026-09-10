package com.fundit.project;

import com.fundit.common.webmvc.openapi.CommonOpenApiConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * auth-service와 동일한 이유로 scanBasePackages를 "com.fundit"으로 넓히지 않고 필요한 설정만 @Import한다 —
 * 넓히면 CommonWebConfig(@LoginUser 리졸버 + InternalGatewaySecretFilter)까지 딸려오는데, 이 서비스는
 * 게이트웨이가 아직 없어 X-User-Id/@LoginUser를 쓰지 않고(CurrentMember/CurrentAdmin이 X-Account-Id를
 * 직접 읽는다) InternalGatewaySecretFilter가 요구하는 internal-api.key 프로퍼티도 없어서
 * 넓히면 그 빈 생성에서 기동 자체가 실패한다.
 */
@SpringBootApplication
@EnableScheduling
@Import(CommonOpenApiConfig.class)
public class ProjectServiceApplication {

    public static void main(String[] arg) {
        SpringApplication.run(ProjectServiceApplication.class, arg);
    }
}
