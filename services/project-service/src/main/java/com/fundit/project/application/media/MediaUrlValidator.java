package com.fundit.project.application.media;

import com.fundit.common.error.BusinessException;
import com.fundit.project.domain.ProjectErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 기존 저장 API(프로젝트 스토리, 리워드)가 fileUrl을 저장하기 전에 재사용하는 공용 검증기.
 * MediaUploadService(업로드 주소 발급)를 거치지 않은 URL이 그대로 저장되지 않도록 막는다.
 * 소유권은 호출부(ProjectService/RewardService의 기존 loadOwned)가 이미 검증했다고 전제하고
 * 여기서는 재검증하지 않는다 — 경로/실존/크기만 확인한다.
 */
@Component
@RequiredArgsConstructor
public class MediaUrlValidator {

    private final MediaStorageClient storageClient;

    public void validate(UUID projectPublicId, String fileUrl, MediaCategory category) {
        String key = storageClient.extractKey(fileUrl)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.INVALID_MEDIA_URL));

        String expectedPrefix = "projects/" + projectPublicId + "/";
        if (!key.startsWith(expectedPrefix)) {
            throw new BusinessException(ProjectErrorCode.INVALID_MEDIA_URL);
        }

        MediaStorageClient.StoredObject stored = storageClient.headObject(key)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.INVALID_MEDIA_URL));
        if (stored.contentLength() > category.getMaxSizeBytes()) {
            throw new BusinessException(ProjectErrorCode.MEDIA_TOO_LARGE);
        }
    }
}
