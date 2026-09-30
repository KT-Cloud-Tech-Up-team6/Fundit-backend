package com.fundit.project.infrastructure.media;

import com.fundit.project.application.media.MediaStorageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
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
 */
@ExtendWith(MockitoExtension.class)
class S3MediaStorageClientUnitTest {

    private static final String BUCKET = "fundit-media-dev-team6";
    private static final String REGION = "ap-northeast-2";
    private static final String CDN_BASE = "https://infrastudy.store/media/";
    private static final String LEGACY_BASE = "https://fundit-media-dev-team6.s3.ap-northeast-2.amazonaws.com/";
    private static final String KEY = "media/projects/01a0ec3c-ea07-705a-945e-c793ff219fb0/01a0ec3e.png";

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
        assertThat(upload.fileUrl()).isEqualTo(CDN_BASE + KEY.substring("media/".length()));
        assertThat(upload.uploadUrl()).startsWith("https://s3-presigned.example/put");
        ArgumentCaptor<PutObjectPresignRequest> signed = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(signed.capture());
        assertThat(signed.getValue().putObjectRequest().key()).isEqualTo(KEY);
        assertThat(storageClient.extractKey(upload.fileUrl())).contains(KEY);
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
        assertThat(upload.fileUrl()).isEqualTo(CDN_BASE + KEY.substring("media/".length()));
    }

    @Test
    void CDN_형식_URL에서_키를_추출한다() {
        // when & then
        assertThat(storageClient.extractKey(CDN_BASE + KEY.substring("media/".length()))).contains(KEY);
    }

    @Test
    void AI_이미지의_서명된_S3_경로와_CDN_경로가_일치한다() {
        // given — 서명은 로컬에서 계산하며 AWS 호출은 없다.
        String key = "media/projects/01a0ec3c-ea07-705a-945e-c793ff219fb0/ai/01a0ec3e.png";
        try (S3Presigner presigner = S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .build()) {
            S3MediaStorageClient client = new S3MediaStorageClient(s3Client, presigner, BUCKET, REGION, CDN_BASE);

            // when
            MediaStorageClient.PresignedUpload upload = client.presignPut(key, "image/png", Duration.ofMinutes(5));

            // then
            assertThat(URI.create(upload.uploadUrl()).getPath()).isEqualTo("/" + key);
            assertThat(upload.fileUrl()).isEqualTo("https://infrastudy.store/" + key);
            assertThat(client.extractKey(upload.fileUrl())).contains(key);
        }
    }

    @Test
    void 기존_S3_형식_URL에서도_키를_추출한다() {
        // when & then
        String legacyKey = KEY.substring("media/".length());
        assertThat(storageClient.extractKey(LEGACY_BASE + legacyKey)).contains(legacyKey);
        assertThat(storageClient.extractKey(LEGACY_BASE + KEY)).contains(KEY);
    }

    @Test
    void 다른_호스트_URL은_키를_돌려주지_않는다() {
        // when & then
        assertThat(storageClient.extractKey("https://evil.example.com/" + KEY)).isEmpty();
        assertThat(storageClient.extractKey("https://other-bucket.s3.ap-northeast-2.amazonaws.com/" + KEY)).isEmpty();
        assertThat(storageClient.extractKey("https://infrastudy.store/other/" + KEY)).isEmpty();
        assertThat(storageClient.extractKey(null)).isEmpty();
    }
}
