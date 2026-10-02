package com.fundit.project.infrastructure.media;

import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.DependencyFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3MediaStorageClientUnitExceptionTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @Test
    void 형식_판별용_읽기가_404_외의_S3_오류면_의존성_장애로_감싼다() {
        // given — 권한 없음(403)은 입력 오류가 아니라 S3 장애다
        S3MediaStorageClient storageClient = new S3MediaStorageClient(
                s3Client, s3Presigner, "fundit-media-dev-team6", "ap-northeast-2", "https://infrastudy.store/media/");
        S3Exception denied = (S3Exception) S3Exception.builder().statusCode(403).build();
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(denied);

        // when & then
        assertThatThrownBy(() -> storageClient.readPrefix("media/projects/p/a.png", 12))
                .isInstanceOfSatisfying(DependencyFailureException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.DEPENDENCY_FAILURE);
                    assertThat(error.getCause()).isSameAs(denied);
                });
    }
}
