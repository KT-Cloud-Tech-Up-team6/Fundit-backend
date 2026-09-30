package com.fundit.project.infrastructure.media;

import com.fundit.project.application.media.MediaStorageClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * MediaStorageClient의 AWS S3 구현체. 발급하는 fileUrl은 CDN 공개 주소
 * ({@code media.public-base-url} + 논리 키)다 — FE가 S3를 직접 호출하면 CDN(캐시·도메인 정책)을
 * 거치지 않고, 인프라가 S3 직접 접근을 막는 순간 이미지가 전부 깨진다.
 *
 * <p><b>논리 키와 S3 키를 구분한다(#205).</b> 바깥(application)이 쓰는 <i>논리 키</i>는
 * {@code projects/{projectId}/...}이고, 실제 S3 object key는 여기에 {@link #S3_KEY_PREFIX}를 붙인
 * {@code media/projects/{projectId}/...}다. CloudFront가 {@code /media/*} 요청을 S3의
 * {@code media/*} key로 넘기면서 {@code /media} 접두사를 떼지 않기 때문이다 — 키에 접두사가 없으면
 * 공개 URL은 맞아 보여도 원본을 못 찾아 전부 404가 된다.
 *
 * <p>공개 URL은 {@code publicBaseUrl}(끝이 {@code /media/})에 <b>논리 키</b>를 붙여 만든다.
 * S3 키를 쓰면 {@code /media/media/...}로 접두사가 두 번 들어간다.
 *
 * <p>키를 되찾는 {@link #extractKey}는 CDN 형식과 기존 S3 가상 호스팅 형식
 * ({@code https://{bucket}.s3.{region}.amazonaws.com/{key}})을 둘 다 받고, 항상 <b>논리 키</b>를
 * 돌려준다 — 이미 S3 형식으로 저장된 프로젝트가 수정·AI 스토리 생성에서 막히면 안 되기 때문이다.
 * 발급은 항상 CDN 형식만 한다.
 *
 * <p>이 URL·키 포맷을 아는 곳은 여기뿐이고, 그 외 계층(application)은 포트 인터페이스만 통해 접근한다.
 */
@Component
public class S3MediaStorageClient implements MediaStorageClient {

    /** CDN(`/media/*`) 경로 규칙에 맞추기 위한 S3 object key 접두사. 공개 URL에는 붙이지 않는다. */
    private static final String S3_KEY_PREFIX = "media/";

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final String publicBaseUrl;
    /** 기존 데이터 호환용 — 읽기(extractKey)에만 쓰고 발급에는 쓰지 않는다. */
    private final String legacyS3BaseUrl;

    public S3MediaStorageClient(S3Client s3Client, S3Presigner s3Presigner,
                                 @Value("${media.s3.bucket}") String bucket,
                                 @Value("${media.s3.region}") String region,
                                 @Value("${media.public-base-url}") String publicBaseUrl) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl : publicBaseUrl + "/";
        this.legacyS3BaseUrl = "https://%s.s3.%s.amazonaws.com/".formatted(bucket, region);
    }

    @Override
    public PresignedUpload presignPut(String key, String contentType, Duration ttl) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key(key))
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
    public String presignGet(String key, Duration ttl) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key(key))
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(getObjectRequest)
                .build();
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }

    @Override
    public Optional<String> extractKey(String fileUrl) {
        if (fileUrl == null) {
            return Optional.empty();
        }
        for (String base : List.of(publicBaseUrl, legacyS3BaseUrl)) {
            if (fileUrl.startsWith(base)) {
                return Optional.of(fileUrl.substring(base.length()));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<StoredObject> headObject(String key) {
        try {
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key(key))
                    .build());
            return Optional.of(new StoredObject(response.contentLength(), response.contentType()));
        } catch (S3Exception e) {
            // HeadObject 404는 본문이 없어 SDK가 NoSuchKeyException이 아닌 일반 S3Exception으로
            // 던지는 경우가 있다 — statusCode로 판별한다. 그 외 오류(권한 등)는 그대로 전파한다.
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /**
     * 논리 키를 실제 S3 object key로 바꾼다. 이미 접두사가 붙은 키(기존 형식 URL에서 추출된 값이
     * 우연히 {@code media/}로 시작하는 경우)를 두 번 붙이지 않는다.
     */
    private String s3Key(String key) {
        return key.startsWith(S3_KEY_PREFIX) ? key : S3_KEY_PREFIX + key;
    }
}
