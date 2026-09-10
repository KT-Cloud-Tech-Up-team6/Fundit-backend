package com.fundit.project.application.media;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.domain.ProjectErrorCode;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

/**
 * 이미지/영상 업로드 주소 발급(S3 Presigned URL, 할일 D) — 파일 바이트는 이 서비스를 거치지 않고
 * 클라이언트가 발급받은 uploadUrl로 S3에 직접 PUT한다. 리워드 이미지도 이 프로젝트 네임스페이스
 * (projects/{projectId}/...)를 쓰므로 소유권은 "프로젝트 소유자(seller)" 하나로 통일해서 검증한다.
 */
@Service
public class MediaUploadService {

    private final ProjectRepository projectRepository;
    private final MediaStorageClient storageClient;
    private final Duration presignTtl;

    public MediaUploadService(ProjectRepository projectRepository, MediaStorageClient storageClient,
                               @Value("${media.upload.presign-ttl-minutes:5}") long presignTtlMinutes) {
        this.projectRepository = projectRepository;
        this.storageClient = storageClient;
        this.presignTtl = Duration.ofMinutes(presignTtlMinutes);
    }

    @Transactional(readOnly = true)
    public MediaStorageClient.PresignedUpload issueUploadUrl(
            UUID sellerId, UUID projectPublicId, String fileName, String contentType, long fileSize) {
        Project project = projectRepository.findByPublicId(projectPublicId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!project.isOwnedBy(sellerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        String extension = extractExtension(fileName);
        MediaCategory category = MediaCategory.resolve(contentType, extension)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.UNSUPPORTED_MEDIA_TYPE));
        if (fileSize <= 0 || fileSize > category.getMaxSizeBytes()) {
            throw new BusinessException(ProjectErrorCode.MEDIA_TOO_LARGE);
        }

        // 클라이언트가 보낸 fileName은 키에 사용하지 않는다(추측 불가 파일명, S5) — 확장자만 재사용.
        String key = "projects/%s/%s.%s".formatted(
                projectPublicId, UuidCreator.getTimeOrderedEpoch(), extension.toLowerCase(Locale.ROOT));
        return storageClient.presignPut(key, contentType, presignTtl);
    }

    private String extractExtension(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            throw new BusinessException(ProjectErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
        return fileName.substring(dot + 1);
    }
}
