package com.fundit.payment.infrastructure.media;

import com.fundit.payment.application.media.MediaStorageClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

/**
 * {@link MediaStorageClient}의 AWS S3 구현체 — project-service {@code S3MediaStorageClient}와 동일 패턴.
 * 발급하는 fileUrl은 S3 직접 주소가 아니라 CDN 공개 주소({@code media.public-base-url} + key)다.
 * 여기엔 키를 되찾는 extractKey가 없어(증빙 URL을 다시 키로 바꾸는 경로가 없다) 기존 S3 형식
 * 호환 분기도 두지 않는다 — 이미 저장된 증빙 URL은 1회성 치환 SQL로 옮긴다.
 */
@Component
public class S3MediaStorageClient implements MediaStorageClient {

    private final S3Presigner s3Presigner;
    private final String bucket;
    private final String publicBaseUrl;

    public S3MediaStorageClient(S3Presigner s3Presigner,
                                 @Value("${media.s3.bucket}") String bucket,
                                 @Value("${media.public-base-url}") String publicBaseUrl) {
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl : publicBaseUrl + "/";
    }

    @Override
    public PresignedUpload presignPut(String key, String contentType, Duration ttl) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(putObjectRequest)
                .build();
        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
        return new PresignedUpload(presigned.url().toString(), publicBaseUrl + key);
    }
}
