package com.fundit.project.application.media;

import java.time.Duration;
import java.util.Optional;

/**
 * 미디어 저장소(S3) 아웃바운드 포트. RewardEventPublisher/InventoryQueryClient와 동일하게
 * 인터페이스는 application에, 실제 구현(AWS SDK)은 infrastructure/media에 둔다.
 */
public interface MediaStorageClient {

    /**
     * 모든 미디어 S3 object key의 접두사. CloudFront가 {@code /media/*} 요청을 S3의
     * {@code media/*} key로 그대로 넘기면서 {@code /media}를 떼지 않으므로, 키 자체가 이 값으로
     * 시작해야 공개 URL로 원본을 찾을 수 있다(#205).
     *
     * <p>키를 만드는 곳이 여럿이라(업로드 주소 발급·AI 이미지 발급·저장 시 경로 검증) 리터럴을
     * 각자 들고 있으면 한 곳만 빠뜨렸을 때 그 경로만 조용히 404가 된다 — 반드시 이 상수를 쓴다.
     */
    String KEY_PREFIX = "media/";

    /** 업로드 주소 발급. presigned PUT URL과 업로드 완료 후 접근할 fileUrl을 함께 반환한다. */
    PresignedUpload presignPut(String key, String contentType, Duration ttl);

    /** AI 입력 이미지용 단기 읽기 URL. */
    String presignGet(String key, Duration ttl);

    /**
     * fileUrl이 이 저장소의 주소 체계로 발급된 것이면 S3 키를, 아니면(다른 호스트·형식 불일치)
     * 빈 값을 반환한다 — URL 포맷(가상 호스팅 스타일 등)은 구현체(infrastructure)만 알아야 한다.
     */
    Optional<String> extractKey(String fileUrl);

    /** 실제 업로드 여부·크기 확인(HeadObject). 객체가 없으면 빈 값. */
    Optional<StoredObject> headObject(String key);

    record PresignedUpload(String uploadUrl, String fileUrl) {
    }

    record StoredObject(long contentLength, String contentType) {

        public StoredObject(long contentLength) {
            this(contentLength, null);
        }
    }
}
