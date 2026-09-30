package com.fundit.project.infrastructure.media;

import com.fundit.project.application.media.MediaStorageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 발급 fileUrl이 CDN 형식인지, 기존 S3 형식으로 저장된 URL도 키로 되돌릴 수 있는지 검증한다 —
 * 후자가 깨지면 이미 저장된 프로젝트의 수정·AI 스토리 생성이 전부 막힌다(#193).
 *
 * <p>여기에 더해 S3 키의 {@code media/} 접두사와 공개 URL이 어긋나지 않는지 확인한다(#205) —
 * 접두사가 빠지면 CloudFront가 원본을 못 찾아 404, 설정값 전체로 URL을 조립하면 {@code /media/media/}가 된다.
 */
@ExtendWith(MockitoExtension.class)
class S3MediaStorageClientUnitTest {

    private static final String BUCKET = "fundit-media-dev-team6";
    private static final String REGION = "ap-northeast-2";
    private static final String CDN_BASE = "https://infrastudy.store/media/";
    private static final String LEGACY_BASE = "https://fundit-media-dev-team6.s3.ap-northeast-2.amazonaws.com/";
    /** S3 object key — /media는 CDN 전용 접두사가 아니라 키의 일부다. */
    private static final String KEY = "media/projects/01a0ec3c-ea07-705a-945e-c793ff219fb0/01a0ec3e.png";
    /** 기존 S3 형식 URL에 들어있는 접두사 없는 키. 그 객체는 실제로 media/ 밖에 있다. */
    private static final String LEGACY_KEY = "projects/01a0ec3c-ea07-705a-945e-c793ff219fb0/01a0ec3e.png";

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private S3MediaStorageClient storageClient;

    @BeforeEach
    void setUp() {
        storageClient = new S3MediaStorageClient(s3Client, s3Presigner, BUCKET, REGION, CDN_BASE);
    }

    @Test
    void 업로드_주소_발급_시_fileUrl이_CDN_주소로_조립된다() throws Exception {
        // given
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://s3-presigned.example/put?X-Amz-Signature=x").toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(presigned);

        // when
        MediaStorageClient.PresignedUpload upload =
                storageClient.presignPut(KEY, "image/png", Duration.ofMinutes(5));

        // then
        assertThat(upload.fileUrl()).isEqualTo("https://infrastudy.store/" + KEY);
        assertThat(upload.uploadUrl()).startsWith("https://s3-presigned.example/put");
    }

    @Test
    void 업로드는_media_접두사가_붙은_S3_key로_발급하고_공개URL에는_media가_한번만_들어간다() throws Exception {
        // given
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://s3-presigned.example/put").toURL());
        ArgumentCaptor<PutObjectPresignRequest> captor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        when(s3Presigner.presignPutObject(captor.capture())).thenReturn(presigned);

        // when
        MediaStorageClient.PresignedUpload upload =
                storageClient.presignPut(KEY, "image/png", Duration.ofMinutes(5));

        // then
        assertThat(captor.getValue().putObjectRequest().key()).isEqualTo(KEY);
        assertThat(upload.fileUrl()).isEqualTo("https://infrastudy.store/" + KEY);
        assertThat(upload.fileUrl()).doesNotContain("/media/media/");
        // 발급한 URL을 다시 키로 되돌리면 같은 S3 키여야 한다(저장 시 검증 경로).
        assertThat(storageClient.extractKey(upload.fileUrl())).contains(KEY);
    }

    @Test
    void 읽기_주소_발급은_키를_그대로_쓴다() throws Exception {
        // given
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://s3-presigned.example/get").toURL());
        ArgumentCaptor<GetObjectPresignRequest> captor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        when(s3Presigner.presignGetObject(captor.capture())).thenReturn(presigned);

        // when
        storageClient.presignGet(KEY, Duration.ofMinutes(5));

        // then
        assertThat(captor.getValue().getObjectRequest().key()).isEqualTo(KEY);
    }

    @Test
    void 실존_확인은_키를_그대로_조회한다() {
        // given
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(10L).contentType("image/png").build());

        // when
        storageClient.headObject(KEY);

        // then
        ArgumentCaptor<HeadObjectRequest> captor = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3Client).headObject(captor.capture());
        assertThat(captor.getValue().key()).isEqualTo(KEY);
    }

    @Test
    void 기존_S3_형식에서_추출한_키는_접두사_없이_그대로_조회한다() {
        // given — 그 객체는 실제로 media/ 밖에 있으므로 접두사를 붙이면 404가 된다.
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(10L).contentType("image/png").build());

        // when
        storageClient.headObject(LEGACY_KEY);

        // then
        ArgumentCaptor<HeadObjectRequest> captor = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3Client).headObject(captor.capture());
        assertThat(captor.getValue().key()).isEqualTo(LEGACY_KEY);
    }

    @Test
    void base_URL_끝에_슬래시가_없어도_키와_사이에_슬래시가_하나만_들어간다() throws Exception {
        // given
        S3MediaStorageClient client = new S3MediaStorageClient(
                s3Client, s3Presigner, BUCKET, REGION, "https://infrastudy.store/media");
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://s3-presigned.example/put").toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(presigned);

        // when
        MediaStorageClient.PresignedUpload upload =
                client.presignPut(KEY, "image/png", Duration.ofMinutes(5));

        // then
        assertThat(upload.fileUrl()).isEqualTo("https://infrastudy.store/" + KEY);
    }

    @Test
    void CDN_형식_URL에서_media가_포함된_키를_추출한다() {
        // when & then — origin만 걷어내므로 media/가 키에 남는다
        assertThat(storageClient.extractKey("https://infrastudy.store/" + KEY)).contains(KEY);
    }

    @Test
    void 기존_S3_형식_URL에서는_접두사_없는_키를_추출한다() {
        // when & then — 그 객체는 media/ 밖에 있으므로 접두사를 붙이면 안 된다
        assertThat(storageClient.extractKey(LEGACY_BASE + LEGACY_KEY)).contains(LEGACY_KEY);
    }

    @Test
    void 다른_호스트_URL은_키를_돌려주지_않는다() {
        // when & then
        assertThat(storageClient.extractKey("https://evil.example.com/" + KEY)).isEmpty();
        assertThat(storageClient.extractKey("https://other-bucket.s3.ap-northeast-2.amazonaws.com/" + KEY)).isEmpty();
        assertThat(storageClient.extractKey("https://infrastudy.store/other/" + LEGACY_KEY)).isEmpty();
        assertThat(storageClient.extractKey(null)).isEmpty();
    }
}
