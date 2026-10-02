package com.fundit.project.application.ai;

import com.fundit.common.error.BusinessException;
import com.fundit.project.application.media.MediaStorageClient;
import com.fundit.project.application.media.MediaUrlValidator;
import com.fundit.project.domain.ProjectErrorCode;
import com.fundit.project.domain.project.IntroContentBlock;
import com.fundit.project.domain.project.IntroContentType;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.reward.Reward;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundingStoryContextFactoryUnitExceptionTest {

    @Mock
    private MediaStorageClient storageClient;

    @Mock
    private MediaUrlValidator mediaUrlValidator;

    private FundingStoryContextFactory factory;
    private final FundingStoryContextFactoryUnitTest fixtures = new FundingStoryContextFactoryUnitTest();

    @BeforeEach
    void setUp() {
        factory = new FundingStoryContextFactory(storageClient, mediaUrlValidator, 15L, 60L);
    }

    @Test
    void 필수_프로젝트_정보가_없으면_계약용_오류를_반환한다() {
        // given
        Project invalid = fixtures.validProject(UUID.randomUUID(), null).toBuilder().businessType(null).build();

        // when & then
        assertThatThrownBy(() -> factory.create(invalid, List.of(fixtures.reward(1L, null))))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ProjectErrorCode.INVALID_PROJECT_DATA));
        assertThatThrownBy(() -> factory.fingerprint(invalid, List.of(fixtures.reward(1L, null))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 리워드와_원본_이미지_검증에_실패하면_Core를_만들지_않는다() {
        // given & when & then — 리워드 필드 누락
        Project project = fixtures.validProject(UUID.randomUUID(), null);
        Reward invalidReward = fixtures.reward(1L, null).toBuilder().description(null).build();
        assertThatThrownBy(() -> factory.create(project, List.of(invalidReward)))
                .isInstanceOf(BusinessException.class);

        // given & when & then — 원본 이미지 확장자 검증 실패
        Project imageProject = fixtures.validProject(UUID.randomUUID(), "https://bucket.example/bad.gif");
        when(storageClient.extractKey("https://bucket.example/bad.gif"))
                .thenReturn(Optional.of("bad.gif"));
        when(storageClient.headObject("bad.gif"))
                .thenReturn(Optional.of(new MediaStorageClient.StoredObject(256L, "image/gif")));

        assertThatThrownBy(() -> factory.create(imageProject, List.of(fixtures.reward(1L, null))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 본문_이미지를_읽을_수_없으면_요약_입력을_만들지_않는다() {
        // given — AI 계약: 이미지 읽기 실패는 텍스트만으로 성공 처리하지 않는다
        Project project = fixtures.validProject(UUID.randomUUID(), null).toBuilder()
                .introContent(List.of(new IntroContentBlock(IntroContentType.IMAGE, "https://bucket.example/gone.png")))
                .build();
        when(storageClient.extractKey("https://bucket.example/gone.png")).thenReturn(Optional.of("gone.png"));
        when(storageClient.headObject("gone.png")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> factory.pageSummarySnapshot(project, List.of()))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ProjectErrorCode.INVALID_PROJECT_DATA));
    }

    @Test
    void 참조_이미지가_대표_리워드_포함_30개를_넘으면_입력_오류다() {
        // given — 기존 1개 + 이전 첨부 28개 + 이번 첨부 2개 = 31개
        FundingStoryAiContracts.FundingStoryContext base = new FundingStoryAiContracts.FundingStoryContext(
                null, List.of(), List.of(new FundingStoryAiContracts.SourceImageRef(
                        "project.cover", null, "https://signed", "image/png", 1L, java.time.Instant.now())));
        List<FundingStoryAiContracts.ChatAttachment> previous = java.util.stream.IntStream.range(0, 28)
                .mapToObj(i -> new FundingStoryAiContracts.ChatAttachment("chat." + i, "https://f/" + i, null, "image/png", 1L))
                .toList();

        // when & then
        assertThatThrownBy(() -> factory.withChatImages(base, previous, 2))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(com.fundit.common.error.CommonErrorCode.INVALID_INPUT));
    }

    @Test
    void 채팅_첨부의_실제_형식이_MIME과_다르면_검증_오류를_그대로_올린다() {
        // given — #224: JPEG 바이트를 image/png로 올린 첨부
        UUID projectId = UUID.randomUUID();
        when(mediaUrlValidator.validateImage(projectId, "https://cdn/a.png"))
                .thenThrow(new BusinessException(ProjectErrorCode.MEDIA_TYPE_MISMATCH));

        // when & then
        assertThatThrownBy(() -> factory.chatImage(projectId, "https://cdn/a.png", null))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ProjectErrorCode.MEDIA_TYPE_MISMATCH));
    }
}
