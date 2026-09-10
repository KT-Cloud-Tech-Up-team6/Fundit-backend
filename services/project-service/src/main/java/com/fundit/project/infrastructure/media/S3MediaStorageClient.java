package com.fundit.project.infrastructure.media;

import com.fundit.project.application.media.MediaStorageClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Optional;

/**
 * MediaStorageClient의 AWS S3 구현체. fileUrl은 가상 호스팅 스타일
 * (https://{bucket}.s3.{region}.amazonaws.com/{key})로 조립한다 — 이 URL 포맷을 아는 곳은
 * 여기뿐이고, 그 외 계층(application)은 포트 인터페이스만 통해 접근한다.
 */
@Component
public class S3MediaStorageClient implements MediaStorageClient {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final String publicBaseUrl;

    public S3MediaStorageClient(S3Client s3Client, S3Presigner s3Presigner,
                                 @Value("${media.s3.bucket}") String bucket,
                                 @Value("${media.s3.region}") String region) {
        this.s3Client = s3Client;
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

    @Override
    public Optional<String> extractKey(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith(publicBaseUrl)) {
            return Optional.empty();
        }
        return Optional.of(fileUrl.substring(publicBaseUrl.length()));
    }

    @Override
    public Optional<StoredObject> headObject(String key) {
        try {
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            return Optional.of(new StoredObject(response.contentLength()));
        } catch (S3Exception e) {
            // HeadObject 404는 본문이 없어 SDK가 NoSuchKeyException이 아닌 일반 S3Exception으로
            // 던지는 경우가 있다 — statusCode로 판별한다. 그 외 오류(권한 등)는 그대로 전파한다.
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }
}
