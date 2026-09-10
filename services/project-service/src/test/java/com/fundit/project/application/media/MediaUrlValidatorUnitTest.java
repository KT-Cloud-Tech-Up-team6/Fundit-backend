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
}
