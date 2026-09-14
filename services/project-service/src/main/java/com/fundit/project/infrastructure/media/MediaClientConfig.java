package com.fundit.project.infrastructure.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;

/**
 * S3Client/S3Presigner 빈 등록. 자격증명은 SDK 기본 Provider Chain(K8s IAM Role/환경변수)을
 * 사용한다 — 코드/설정에 액세스 키를 하드코딩하지 않는다(security.md S9, S7).
 */
@Configuration
public class MediaClientConfig {

    @Bean
    public S3Client s3Client(@Value("${media.s3.region}") String region) {
        return S3Client.builder()
                .region(Region.of(region))
                // HeadObject는 서비스 간 동기 호출과 동일하게 타임아웃 없이 두지 않는다(security.md 취지).
                .overrideConfiguration(b -> b.apiCallTimeout(Duration.ofSeconds(5)))
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(@Value("${media.s3.region}") String region) {
        // presign 자체는 네트워크 호출 없이 로컬에서 서명만 계산한다.
        return S3Presigner.builder()
                .region(Region.of(region))
                .build();
    }
}
