package com.fundit.project.application.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaUrlValidatorUnitTest {

    @Mock
    private MediaStorageClient storageClient;

    @InjectMocks
    private MediaUrlValidator mediaUrlValidator;

    @Test
    void 경로와_실존_크기가_모두_유효하면_통과한다() {
        // given
        UUID projectId = UUID.randomUUID();
        String key = "projects/" + projectId + "/a.jpg";
        String fileUrl = "https://bucket.s3.ap-northeast-2.amazonaws.com/" + key;
        when(storageClient.extractKey(fileUrl)).thenReturn(Optional.of(key));
        when(storageClient.headObject(key)).thenReturn(Optional.of(new MediaStorageClient.StoredObject(1024L)));

        // when & then
        assertThatCode(() -> mediaUrlValidator.validate(projectId, fileUrl, MediaCategory.IMAGE))
                .doesNotThrowAnyException();
    }

    @Test
    void CDN_형식의_media_경로_키도_통과한다() {
        // given — 키에 media/ 접두사가 붙은 뒤(#205) 발급된 URL
        UUID projectId = UUID.randomUUID();
        String key = MediaStorageClient.KEY_PREFIX + "projects/" + projectId + "/a.jpg";
        String fileUrl = "https://infrastudy.store/" + key;
        when(storageClient.extractKey(fileUrl)).thenReturn(Optional.of(key));
        when(storageClient.headObject(key)).thenReturn(Optional.of(new MediaStorageClient.StoredObject(1024L)));

        // when & then
        assertThatCode(() -> mediaUrlValidator.validate(projectId, fileUrl, MediaCategory.IMAGE))
                .doesNotThrowAnyException();
    }

    @Test
    void 기존_S3_형식으로_저장된_URL로_재저장해도_통과한다() {
        // given — CDN 주소로 바꾸기 전(#193)에 저장된 프로젝트를 수정하는 경우
        UUID projectId = UUID.randomUUID();
        String key = "projects/" + projectId + "/a.jpg";
        String legacyFileUrl = "https://fundit-media-dev-team6.s3.ap-northeast-2.amazonaws.com/" + key;
        when(storageClient.extractKey(legacyFileUrl)).thenReturn(Optional.of(key));
        when(storageClient.headObject(key)).thenReturn(Optional.of(new MediaStorageClient.StoredObject(1024L)));

        // when & then
        assertThatCode(() -> mediaUrlValidator.validate(projectId, legacyFileUrl, MediaCategory.IMAGE))
                .doesNotThrowAnyException();
    }
}
