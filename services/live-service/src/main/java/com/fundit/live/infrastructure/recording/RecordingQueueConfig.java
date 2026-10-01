package com.fundit.live.infrastructure.recording;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.time.Duration;

/**
 * 녹화 완료 이벤트 큐(EventBridge → SQS, #222) 클라이언트. 큐 URL이 있을 때만 뜬다 — 로컬·테스트엔 큐가 없다.
 *
 * <p>자격증명은 SDK 기본 Provider Chain(파드 IRSA)에 맡긴다. 타임아웃은 {@code AwsIvsConfig}와 같은 값을 쓴다
 * (루트 CLAUDE.md: 동기 호출에 기본값 금지).
 */
@Configuration
@ConditionalOnExpression("!'${live.recording.queue-url:}'.isBlank()")
public class RecordingQueueConfig {

    @Bean
    public SqsClient recordingQueueSqsClient(
            @Value("${live.ivs.region}") String region,
            @Value("${live.ivs.api-timeout-ms}") long timeoutMs) {
        return SqsClient.builder()
                .region(Region.of(region))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallAttemptTimeout(Duration.ofMillis(timeoutMs))
                        .apiCallTimeout(Duration.ofMillis(timeoutMs * 2))
                        .build())
                .build();
    }
}
