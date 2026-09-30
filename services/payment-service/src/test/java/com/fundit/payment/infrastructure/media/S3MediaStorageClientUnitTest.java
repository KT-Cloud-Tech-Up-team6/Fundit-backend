package com.fundit.payment.infrastructure.media;

import com.fundit.payment.application.media.MediaStorageClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 증빙 업로드 주소 발급의 fileUrl이 S3 직접 주소가 아니라 CDN 공개 주소로 조립되는지 검증한다(#193). */
@ExtendWith(MockitoExtension.class)
class S3MediaStorageClientUnitTest {

    private static final String CDN_BASE = "https://infrastudy.store/media/";
    private static final String KEY = "refunds/018f9a1b-0000-7000-8000-000000000000/evidence.jpg";

    @Mock
    private S3Presigner s3Presigner;

    @Test
    void 업로드_주소_발급_시_fileUrl이_CDN_주소로_조립된다() throws Exception {
        // given
        S3MediaStorageClient storageClient =
                new S3MediaStorageClient(s3Presigner, "fundit-media-dev-team6", CDN_BASE);
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://s3-presigned.example/put?X-Amz-Signature=x").toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

        // when
        MediaStorageClient.PresignedUpload upload =
                storageClient.presignPut(KEY, "image/jpeg", Duration.ofMinutes(5));

        // then
        assertThat(upload.fileUrl()).isEqualTo(CDN_BASE + KEY);
        assertThat(upload.uploadUrl()).startsWith("https://s3-presigned.example/put");
    }

    @Test
    void base_URL_끝에_슬래시가_없어도_키와_사이에_슬래시가_하나만_들어간다() throws Exception {
        // given
        S3MediaStorageClient storageClient =
                new S3MediaStorageClient(s3Presigner, "fundit-media-dev-team6", "https://infrastudy.store/media");
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://s3-presigned.example/put").toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

        // when
        MediaStorageClient.PresignedUpload upload =
                storageClient.presignPut(KEY, "image/jpeg", Duration.ofMinutes(5));

        // then
        assertThat(upload.fileUrl()).isEqualTo(CDN_BASE + KEY);
    }
}
