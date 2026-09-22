package com.fundit.live.infrastructure.ivs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ivschat.IvschatClient;

import java.time.Duration;

/**
 * IVS·IVS Chat SDK 클라이언트. {@code live.ivs.mode=aws}일 때만 뜬다.
 *
 * <p>자격증명은 SDK 기본 Provider Chain에 맡긴다 — 로컬은 AWS 프로필/환경변수, 클라우드는
 * 배포가 주입한 환경변수를 읽는다. 코드·설정에 키를 두지 않는다(security.md S7·S9).
 *
 * <p>타임아웃을 명시한다 — SDK 기본값은 재시도를 포함해 수십 초까지 늘어나, IVS가 느리면
 * 방송 시작 요청이 그만큼 붙잡힌다(루트 CLAUDE.md: 동기 호출에 기본값 금지).
 */
@Configuration
@ConditionalOnProperty(name = "live.ivs.mode", havingValue = "aws")
public class AwsIvsConfig {

    // 우리 포트(application.ivs.IvsClient)와 이름이 같아 SDK 쪽은 FQCN으로 쓴다.
    @Bean
    public software.amazon.awssdk.services.ivs.IvsClient awsIvsSdkClient(
            @Value("${live.ivs.region}") String region,
            @Value("${live.ivs.api-timeout-ms}") long timeoutMs) {
        return software.amazon.awssdk.services.ivs.IvsClient.builder()
                .region(Region.of(region))
                .overrideConfiguration(timeouts(timeoutMs))
                .build();
    }

    @Bean
    public IvschatClient awsIvschatSdkClient(
            @Value("${live.ivs.region}") String region,
            @Value("${live.ivs.api-timeout-ms}") long timeoutMs) {
        return IvschatClient.builder()
                .region(Region.of(region))
                .overrideConfiguration(timeouts(timeoutMs))
                .build();
    }

    private static ClientOverrideConfiguration timeouts(long timeoutMs) {
        return ClientOverrideConfiguration.builder()
                .apiCallAttemptTimeout(Duration.ofMillis(timeoutMs))
                .apiCallTimeout(Duration.ofMillis(timeoutMs * 2))
                .build();
    }
}
