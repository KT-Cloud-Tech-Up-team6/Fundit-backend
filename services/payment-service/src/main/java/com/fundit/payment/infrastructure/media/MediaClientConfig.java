package com.fundit.payment.infrastructure.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3Presigner 빈 등록 — project-service {@code MediaClientConfig}와 동일 패턴. 자격증명은 SDK 기본
 * Provider Chain(K8s IAM Role/환경변수)을 사용한다(security.md S9, S7). 업로드 확인(HeadObject)은
 * F09 범위에서 필요 없어 S3Client는 두지 않는다(presign은 네트워크 호출 없이 로컬 서명만 계산).
 */
@Configuration
public class MediaClientConfig {

    @Bean
    public S3Presigner s3Presigner(@Value("${media.s3.region}") String region) {
        return S3Presigner.builder()
                .region(Region.of(region))
                .build();
    }
}
