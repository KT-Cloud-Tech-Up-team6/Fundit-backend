package com.fundit.project.application.ai;

import com.fundit.project.application.media.MediaStorageClient;
import com.fundit.project.domain.project.BusinessType;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundingStoryContextFactoryUnitTest {

    @Mock
    private MediaStorageClient storageClient;

    private FundingStoryContextFactory factory;

    @BeforeEach
    void setUp() {
        factory = new FundingStoryContextFactory(storageClient, 15L);
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
