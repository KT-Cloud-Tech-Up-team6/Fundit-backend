package com.fundit.project.application.media;

import com.fundit.common.error.BusinessException;
import com.fundit.project.domain.ProjectErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaUrlValidatorUnitExceptionTest {

    @Mock
    private MediaStorageClient storageClient;

    @InjectMocks
    private MediaUrlValidator mediaUrlValidator;

    @Test
    void 발급받은_주소_체계가_아니면_예외가_발생한다() {
        // given
        UUID projectId = UUID.randomUUID();
        String fileUrl = "https://attacker.example.com/a.jpg";
        when(storageClient.extractKey(fileUrl)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> mediaUrlValidator.validate(projectId, fileUrl, MediaCategory.IMAGE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.INVALID_MEDIA_URL);
    }

    @Test
    void 다른_프로젝트_경로면_예외가_발생한다() {
        // given
        UUID projectId = UUID.randomUUID();
        String key = "projects/" + UUID.randomUUID() + "/a.jpg";
        String fileUrl = "https://bucket.s3.ap-northeast-2.amazonaws.com/" + key;
        when(storageClient.extractKey(fileUrl)).thenReturn(Optional.of(key));

        // when & then
        assertThatThrownBy(() -> mediaUrlValidator.validate(projectId, fileUrl, MediaCategory.IMAGE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.INVALID_MEDIA_URL);
    }

    @Test
    void S3에_실제로_업로드되지_않았으면_예외가_발생한다() {
        // given
        UUID projectId = UUID.randomUUID();
        String key = "projects/" + projectId + "/a.jpg";
        String fileUrl = "https://bucket.s3.ap-northeast-2.amazonaws.com/" + key;
        when(storageClient.extractKey(fileUrl)).thenReturn(Optional.of(key));
        when(storageClient.headObject(key)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> mediaUrlValidator.validate(projectId, fileUrl, MediaCategory.IMAGE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.INVALID_MEDIA_URL);
    }

    @Test
    void 실제_업로드_크기가_정책을_초과하면_예외가_발생한다() {
        // given
        UUID projectId = UUID.randomUUID();
        String key = "projects/" + projectId + "/a.jpg";
        String fileUrl = "https://bucket.s3.ap-northeast-2.amazonaws.com/" + key;
        when(storageClient.extractKey(fileUrl)).thenReturn(Optional.of(key));
        when(storageClient.headObject(key)).thenReturn(Optional.of(
                new MediaStorageClient.StoredObject(MediaCategory.IMAGE.getMaxSizeBytes() + 1)));

        // when & then
        assertThatThrownBy(() -> mediaUrlValidator.validate(projectId, fileUrl, MediaCategory.IMAGE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.MEDIA_TOO_LARGE);
    }
}
