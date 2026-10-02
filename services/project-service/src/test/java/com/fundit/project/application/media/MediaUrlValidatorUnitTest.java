package com.fundit.project.application.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaUrlValidatorUnitTest {

    @Mock
    private MediaStorageClient storageClient;

    @InjectMocks
    private MediaUrlValidator mediaUrlValidator;

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1};
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    static final byte[] WEBP = {'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P'};

    private static MediaStorageClient.StoredObject jpeg() {
        return new MediaStorageClient.StoredObject(1024L, "image/jpeg");
    }

    @Test
    void 경로와_실존_크기가_모두_유효하면_통과한다() {
        // given
        UUID projectId = UUID.randomUUID();
        String key = "projects/" + projectId + "/a.jpg";
        String fileUrl = "https://bucket.s3.ap-northeast-2.amazonaws.com/" + key;
        when(storageClient.extractKey(fileUrl)).thenReturn(Optional.of(key));
        when(storageClient.headObject(key)).thenReturn(Optional.of(jpeg()));
        when(storageClient.readPrefix(key, 12)).thenReturn(JPEG);

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
        when(storageClient.headObject(key)).thenReturn(Optional.of(jpeg()));
        when(storageClient.readPrefix(key, 12)).thenReturn(JPEG);

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
        when(storageClient.headObject(key)).thenReturn(Optional.of(jpeg()));
        when(storageClient.readPrefix(key, 12)).thenReturn(JPEG);

        // when & then
        assertThatCode(() -> mediaUrlValidator.validate(projectId, legacyFileUrl, MediaCategory.IMAGE))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({"image/png, PNG", "image/webp, WEBP", "IMAGE/JPG, JPEG"})
    void 실제_바이트_형식이_저장된_MIME과_같으면_통과한다(String contentType, String format) {
        // given — image/jpg는 비표준이지만 브라우저가 보내기도 해 jpeg로 본다
        UUID projectId = UUID.randomUUID();
        String key = MediaStorageClient.KEY_PREFIX + "projects/" + projectId + "/a.img";
        String fileUrl = "https://infrastudy.store/" + key;
        byte[] head = switch (format) {
            case "PNG" -> PNG;
            case "WEBP" -> WEBP;
            default -> JPEG;
        };
        when(storageClient.extractKey(fileUrl)).thenReturn(Optional.of(key));
        when(storageClient.headObject(key)).thenReturn(Optional.of(new MediaStorageClient.StoredObject(1024L, contentType)));
        when(storageClient.readPrefix(key, 12)).thenReturn(head);

        // when
        MediaUrlValidator.ValidatedMedia media = mediaUrlValidator.validateImage(projectId, fileUrl);

        // then
        assertThat(media.key()).isEqualTo(key);
    }

    @Test
    void 영상은_실제_형식을_읽지_않는다() {
        // given
        UUID projectId = UUID.randomUUID();
        String key = "projects/" + projectId + "/a.mp4";
        String fileUrl = "https://bucket.s3.ap-northeast-2.amazonaws.com/" + key;
        when(storageClient.extractKey(fileUrl)).thenReturn(Optional.of(key));
        when(storageClient.headObject(key)).thenReturn(Optional.of(new MediaStorageClient.StoredObject(1024L, "video/mp4")));

        // when
        mediaUrlValidator.validate(projectId, fileUrl, MediaCategory.VIDEO);

        // then
        verify(storageClient, never()).readPrefix(anyString(), anyInt());
    }
}
