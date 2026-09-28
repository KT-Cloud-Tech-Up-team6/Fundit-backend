package com.fundit.project.application.ai;

import com.fundit.project.application.media.MediaStorageClient;
import com.fundit.project.domain.project.BusinessType;
import com.fundit.project.domain.project.IntroContentBlock;
import com.fundit.project.domain.project.IntroContentType;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.domain.reward.Reward;
import com.fundit.project.domain.reward.RewardOptionGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundingStoryContextFactoryUnitTest {

    @Mock
    private MediaStorageClient storageClient;

    private FundingStoryContextFactory factory;

    @BeforeEach
    void setUp() {
        factory = new FundingStoryContextFactory(storageClient, 15L, 60L);
    }

    @Test
    void Core_정보와_최대_세_개의_리워드_이미지_참조를_생성한다() {
        // given
        UUID projectId = UUID.randomUUID();
        Project project = validProject(projectId, "https://bucket.example/cover.png");
        List<Reward> rewards = List.of(
                reward(1L, "https://bucket.example/reward-1.png"),
                reward(2L, null),
                reward(3L, "https://bucket.example/reward-3.png"),
                reward(4L, "https://bucket.example/reward-4.png"));

        when(storageClient.extractKey(any())).thenAnswer(invocation ->
                Optional.of(invocation.getArgument(0, String.class).substring("https://bucket.example/".length())));
        when(storageClient.headObject(any()))
                .thenReturn(Optional.of(new MediaStorageClient.StoredObject(256L, "image/png")));
        when(storageClient.presignGet(any(), any())).thenReturn("https://signed.example/image");

        // when
        FundingStoryAiContracts.FundingStoryContext context = factory.create(project, rewards);

        // then
        assertThat(context.project().business_type()).isEqualTo("SOLE");
        assertThat(context.project().category().major()).isEqualTo("테크");
        assertThat(context.rewards()).hasSize(3);
        assertThat(context.rewards().get(0).options()).containsExactly(
                new FundingStoryAiContracts.RewardOption("색상", List.of("화이트", "블랙")));
        assertThat(context.source_images()).extracting("slot_id")
                .containsExactly("project.cover", "reward.1", "reward.3");
        assertThat(context.source_images().get(0).expires_at()).isNotNull();
    }

    @Test
    void fingerprint는_같은_Core에_대해_안정적이고_리워드_변경을_구분한다() {
        // given
        Project project = validProject(UUID.randomUUID(), null);
        Reward original = reward(1L, null);

        // when
        String first = factory.fingerprint(project, List.of(original));
        String second = factory.fingerprint(project, List.of(original));
        String changed = factory.fingerprint(project, List.of(original.toBuilder().name("변경 리워드").build()));

        // then
        assertThat(first).isEqualTo(second).hasSize(64);
        assertThat(changed).isNotEqualTo(first);
    }

    @Test
    void 요약_입력은_본문_순서대로_TEXT와_IMAGE만_담고_GIF는_건너뛴다() {
        // given
        Project project = validProject(UUID.randomUUID(), null).toBuilder()
                .introContent(List.of(
                        new IntroContentBlock(IntroContentType.TEXT, "<p>첫 문단</p>"),
                        new IntroContentBlock(IntroContentType.IMAGE, "https://bucket.example/body.png"),
                        new IntroContentBlock(IntroContentType.VIDEO_URL, "https://youtube.example/v"),
                        new IntroContentBlock(IntroContentType.IMAGE, "https://bucket.example/anim.gif"),
                        new IntroContentBlock(IntroContentType.TEXT, " "),
                        new IntroContentBlock(IntroContentType.TEXT, "<p>끝 문단</p>")))
                .build();
        when(storageClient.extractKey(any())).thenAnswer(invocation ->
                Optional.of(invocation.getArgument(0, String.class).substring("https://bucket.example/".length())));
        when(storageClient.headObject("body.png"))
                .thenReturn(Optional.of(new MediaStorageClient.StoredObject(256L, "image/png")));
        when(storageClient.headObject("anim.gif"))
                .thenReturn(Optional.of(new MediaStorageClient.StoredObject(256L, "image/gif")));
        when(storageClient.presignGet(eq("body.png"), eq(java.time.Duration.ofMinutes(60))))
                .thenReturn("https://signed.example/body");

        // when
        FundingStoryAiContracts.ProjectSnapshot snapshot = factory.pageSummarySnapshot(project, List.of(reward(1L, null)));

        // then
        assertThat(snapshot.title()).isEqualTo("프로젝트");
        assertThat(snapshot.category()).isEqualTo("테크/가전");
        assertThat(snapshot.rewards()).containsExactly(
                new FundingStoryAiContracts.PageSummaryReward("리워드 1", "리워드 설명", 10_000L));
        assertThat(snapshot.story_content()).extracting("type", "value").containsExactly(
                org.assertj.core.groups.Tuple.tuple("TEXT", "<p>첫 문단</p>"),
                org.assertj.core.groups.Tuple.tuple("IMAGE", "https://bucket.example/body.png"),
                org.assertj.core.groups.Tuple.tuple("TEXT", "<p>끝 문단</p>"));
        FundingStoryAiContracts.StoryContentBlock image = snapshot.story_content().get(1);
        assertThat(image.read_url()).isEqualTo("https://signed.example/body");
        assertThat(image.content_type()).isEqualTo("image/png");
        assertThat(image.file_size()).isEqualTo(256L);
        assertThat(image.expires_at()).isAfter(java.time.Instant.now().plusSeconds(59 * 60));
    }

    @Test
    void 요약_해시는_입력이_같으면_같고_본문이_바뀌면_달라진다() {
        // given
        Project project = validProject(UUID.randomUUID(), null).toBuilder()
                .introContent(List.of(new IntroContentBlock(IntroContentType.TEXT, "본문"))).build();
        Project edited = project.toBuilder()
                .introContent(List.of(new IntroContentBlock(IntroContentType.TEXT, "수정 본문"))).build();
        Project goalChanged = project.toBuilder().goalAmount(9_000_000L).build();

        // when
        String hash = factory.pageSummaryHash(project, List.of(reward(1L, null)));

        // then — 요약 입력이 아닌 목표금액 변경은 해시를 바꾸지 않는다
        assertThat(hash).hasSize(64)
                .isEqualTo(factory.pageSummaryHash(goalChanged, List.of(reward(1L, null))))
                .isNotEqualTo(factory.pageSummaryHash(edited, List.of(reward(1L, null))));
    }

    Project validProject(UUID publicId, String coverImageUrl) {
        return Project.builder()
                .id(1L)
                .publicId(publicId)
                .sellerId(UUID.randomUUID())
                .businessType(BusinessType.SOLE)
                .categoryMajor("테크")
                .categoryMinor("가전")
                .title("프로젝트")
                .goalAmount(1_000_000L)
                .coverImageUrl(coverImageUrl)
                .status(ProjectStatus.DRAFT)
                .build();
    }

    Reward reward(Long id, String imageUrl) {
        return Reward.builder()
                .id(id)
                .projectId(1L)
                .name("리워드 " + id)
                .description("리워드 설명")
                .imageUrl(imageUrl)
                .price(10_000L)
                .isLimited(false)
                .quantity(null)
                .isEarlyBird(false)
                .optionGroups(List.of(new RewardOptionGroup("색상", List.of("화이트", "블랙"))))
                .build();
    }
}
