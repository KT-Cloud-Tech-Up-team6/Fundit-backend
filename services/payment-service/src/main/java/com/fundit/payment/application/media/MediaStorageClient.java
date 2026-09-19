package com.fundit.payment.application.media;

import java.time.Duration;

/**
 * 미디어 저장소(S3) 아웃바운드 포트 — project-service {@code MediaStorageClient}와 동일 계약
 * (S3 인프라를 공유하되 키 네임스페이스만 다르다: {@code refunds/} vs {@code projects/}).
 */
public interface MediaStorageClient {

    /** 업로드 주소 발급. presigned PUT URL과 업로드 완료 후 접근할 fileUrl을 함께 반환한다. */
    PresignedUpload presignPut(String key, String contentType, Duration ttl);

    record PresignedUpload(String uploadUrl, String fileUrl) {
    }
}
