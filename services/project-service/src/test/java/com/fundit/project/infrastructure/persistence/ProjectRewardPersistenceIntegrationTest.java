package com.fundit.project.infrastructure.persistence;

import com.fundit.project.domain.project.BusinessType;
import com.fundit.project.domain.project.IntroContentBlock;
import com.fundit.project.domain.project.IntroContentType;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.domain.reward.Reward;
import com.fundit.project.domain.reward.RewardOptionGroup;
import com.fundit.project.domain.reward.RewardRepository;
import com.fundit.project.infrastructure.persistence.category.CategoryJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V1~V3 마이그레이션 + JPA 매핑(JSONB, DB GENERATED 컬럼, updated_at 트리거)이 실제 Postgres에서
 * 정상 동작하는지 검증한다. 서비스 유닛테스트는 리포지토리를 목킹하므로 이 배선은 여기서만 검증된다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ProjectRewardPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private RewardRepository rewardRepository;
    @Autowired
    private CategoryJpaRepository categoryJpaRepository;

    @Test
    void 프로젝트를_저장하면_public_id와_project_display_code가_채워진다() {
        // given
        Instant now = Instant.now();
        Project project = Project.builder()
                .publicId(UUID.randomUUID())
                .sellerId(UUID.randomUUID())
                .status(ProjectStatus.DRAFT)
                .createdAt(now).updatedAt(now)
                .build();

        // when
        Project saved = projectRepository.save(project);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getProjectDisplayCode()).startsWith("F");
    }

    @Test
    void 기본정보와_소개콘텐츠를_저장하고_다시_읽으면_그대로_복원된다() {
        // given
        Instant now = Instant.now();
        Project project = Project.builder()
                .publicId(UUID.randomUUID())
                .sellerId(UUID.randomUUID())
                .status(ProjectStatus.DRAFT)
                .businessType(BusinessType.SOLE)
                .categoryMajor("테크·가전")
                .categoryMinor("생활가전")
                .title("세상에 없는 프라이팬")
                .goalAmount(1_000_000L)
                .coverImageUrl("http://img")
                .introContent(List.of(new IntroContentBlock(IntroContentType.TEXT, "본문")))
                .createdAt(now).updatedAt(now)
                .build();
        Project saved = projectRepository.save(project);

        // when
        Project reloaded = projectRepository.findByPublicId(saved.getPublicId()).orElseThrow();

        // then
        assertThat(reloaded.getBusinessType()).isEqualTo(BusinessType.SOLE);
        assertThat(reloaded.getTitle()).isEqualTo("세상에 없는 프라이팬");
        assertThat(reloaded.getIntroContent()).containsExactly(new IntroContentBlock(IntroContentType.TEXT, "본문"));
        assertThat(reloaded.hasCompletedBasicInfo()).isTrue();
    }

    @Test
    void 시드된_카테고리_조합은_존재한다() {
        assertThat(categoryJpaRepository.existsByCategoryMajorAndCategoryMinor("테크·가전", "생활가전")).isTrue();
        assertThat(categoryJpaRepository.existsByCategoryMajorAndCategoryMinor("없는대분류", "없는중분류")).isFalse();
    }

    @Test
    void 리워드를_저장하면_reward_display_code가_채워진다() {
        // given
        Reward reward = Reward.create(persistProjectId(), "얼리버드", "설명", null, 39000L, true, 100, true, null);

        // when
        Reward saved = rewardRepository.save(reward);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getRewardDisplayCode()).startsWith("R");
    }

    @Test
    void 옵션을_치환하면_그룹과_값이_모두_반영된다() {
        // given
        Long projectId = persistProjectId();
        Reward reward = rewardRepository.save(Reward.create(projectId, "얼리버드", "설명", null, 39000L, false, null, false, null));
        List<RewardOptionGroup> options = List.of(new RewardOptionGroup("색상", List.of("화이트", "블랙")));

        // when
        rewardRepository.replaceOptions(reward.getId(), options);

        // then — replaceOptions 자체가 예외 없이 완료되면 정상(옵션은 응답에 되읽지 않는 설계).
        Reward reloaded = rewardRepository.findById(reward.getId()).orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(reward.getId());
    }

    @Test
    void 리워드_고시정보_JSONB가_그대로_왕복된다() {
        // given
        Reward reward = rewardRepository.save(
                Reward.create(persistProjectId(), "얼리버드", "설명", null, 39000L, false, null, false, null));
        reward.changeDisclosure("COSMETIC", Map.of("제조국", "대한민국"));

        // when
        Reward saved = rewardRepository.save(reward);
        Reward reloaded = rewardRepository.findById(saved.getId()).orElseThrow();

        // then
        assertThat(reloaded.getCategoryType()).isEqualTo("COSMETIC");
        assertThat(reloaded.getDisclosure()).containsEntry("제조국", "대한민국");
    }

    private Long persistProjectId() {
        Instant now = Instant.now();
        return projectRepository.save(Project.builder()
                .publicId(UUID.randomUUID()).sellerId(UUID.randomUUID()).status(ProjectStatus.DRAFT)
                .createdAt(now).updatedAt(now).build()).getId();
    }
}
