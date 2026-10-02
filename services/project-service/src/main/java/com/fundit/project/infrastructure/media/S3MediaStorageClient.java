package com.fundit.project.infrastructure.media;

import com.fundit.common.error.DependencyFailureException;
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

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * MediaStorageClient의 AWS S3 구현체. 발급하는 fileUrl은 CDN 공개 주소
 * ({@code media.public-base-url}의 <b>origin</b> + S3 키)다 — FE가 S3를 직접 호출하면
 * CDN(캐시·도메인 정책)을 거치지 않고, 인프라가 S3 직접 접근을 막는 순간 이미지가 전부 깨진다.
 *
 * <p><b>{@code /media}는 CDN 전용 접두사가 아니라 S3 키의 일부다(#205).</b> CloudFront가
 * {@code /media/*} 요청을 S3의 {@code media/*} key로 그대로 넘기면서 접두사를 떼지 않기 때문에,
 * 키 자체가 {@link MediaStorageClient#KEY_PREFIX}로 시작한다. 그래서 공개 URL은 설정값 전체가 아니라
 * 그 <b>origin</b>에 키를 붙여 만든다 — 설정값({@code .../media/})을 그대로 쓰면 {@code /media/media/}가 된다.
 *
 * <p>키를 되찾는 {@link #extractKey}는 CDN 형식과 기존 S3 가상 호스팅 형식
 * ({@code https://{bucket}.s3.{region}.amazonaws.com/{key}})을 둘 다 받는다 — 이미 S3 형식으로
 * 저장된 프로젝트가 수정·AI 스토리 생성에서 막히면 안 되기 때문이다. 기존 형식에서는 접두사 없는
 * 키({@code projects/...})가 나오는데, 그 객체는 실제로 {@code media/} 밖에 있으므로 그대로 조회해야
 * 맞다. 발급은 항상 CDN 형식만 한다.
 *
 * <p>이 URL 포맷을 아는 곳은 여기뿐이고, 그 외 계층(application)은 포트 인터페이스만 통해 접근한다.
 */
@Component
public class S3MediaStorageClient implements MediaStorageClient {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final String publicBaseUrl;
    /** 공개 URL 조립용 — 키가 media/로 시작하므로 설정값의 경로가 아니라 origin만 쓴다. */
    private final String publicOriginUrl;
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
        this.publicOriginUrl = URI.create(this.publicBaseUrl).resolve("/").toString();
        this.legacyS3BaseUrl = "https://%s.s3.%s.amazonaws.com/".formatted(bucket, region);
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
        return new PresignedUpload(presigned.url().toString(), publicOriginUrl + key);
    }

    @Override
    public String presignGet(String key, Duration ttl) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
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
        if (fileUrl.startsWith(publicBaseUrl)) {
            // /media/는 CDN 전용 접두사가 아니라 S3 키의 일부라 origin만 걷어낸다.
            return Optional.of(fileUrl.substring(publicOriginUrl.length()));
        }
        if (fileUrl.startsWith(legacyS3BaseUrl)) {
            // 기존 형식에는 접두사가 없다 — 그 객체는 실제로 media/ 밖에 있으므로 그대로 돌려준다.
            return Optional.of(fileUrl.substring(legacyS3BaseUrl.length()));
        }
        return Optional.empty();
    }

    @Override
    public Optional<StoredObject> headObject(String key) {
        try {
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
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

    @Override
    public byte[] readPrefix(String key, int length) {
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .range("bytes=0-" + (length - 1))
                    .build()).asByteArray();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return new byte[0];
            }
            // 권한·스로틀링 등 S3 장애는 입력 오류가 아니다 — 500 대신 503으로(error-handling 규칙).
            throw new DependencyFailureException(e);
        }
    }
}
