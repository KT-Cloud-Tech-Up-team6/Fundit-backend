package com.fundit.payment.infrastructure.media;

import com.fundit.payment.application.media.MediaStorageClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

/** {@link MediaStorageClient}의 AWS S3 구현체 — project-service {@code S3MediaStorageClient}와 동일 패턴. */
@Component
public class S3MediaStorageClient implements MediaStorageClient {

    private final S3Presigner s3Presigner;
    private final String bucket;
    private final String publicBaseUrl;

    public S3MediaStorageClient(S3Presigner s3Presigner,
                                 @Value("${media.s3.bucket}") String bucket,
                                 @Value("${media.s3.region}") String region) {
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.publicBaseUrl = "https://%s.s3.%s.amazonaws.com/".formatted(bucket, region);
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
