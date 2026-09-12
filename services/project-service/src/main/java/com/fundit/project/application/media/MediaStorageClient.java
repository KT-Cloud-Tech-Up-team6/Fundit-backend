package com.fundit.project.application.media;

import java.time.Duration;
import java.util.Optional;

/**
 * 미디어 저장소(S3) 아웃바운드 포트. RewardEventPublisher/InventoryQueryClient와 동일하게
 * 인터페이스는 application에, 실제 구현(AWS SDK)은 infrastructure/media에 둔다.
 */
public interface MediaStorageClient {

    /** 업로드 주소 발급. presigned PUT URL과 업로드 완료 후 접근할 fileUrl을 함께 반환한다. */
    PresignedUpload presignPut(String key, String contentType, Duration ttl);

    /**
     * fileUrl이 이 저장소의 주소 체계로 발급된 것이면 S3 키를, 아니면(다른 호스트·형식 불일치)
     * 빈 값을 반환한다 — URL 포맷(가상 호스팅 스타일 등)은 구현체(infrastructure)만 알아야 한다.
     */
    Optional<String> extractKey(String fileUrl);

    /** 실제 업로드 여부·크기 확인(HeadObject). 객체가 없으면 빈 값. */
    Optional<StoredObject> headObject(String key);

    record PresignedUpload(String uploadUrl, String fileUrl) {
    }

    record StoredObject(long contentLength) {
    }
}
