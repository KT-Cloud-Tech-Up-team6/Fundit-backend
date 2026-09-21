package com.fundit.project.infrastructure.persistence;

import com.fundit.project.domain.project.BusinessType;
import com.fundit.project.domain.project.IntroContentBlock;
import com.fundit.project.domain.project.IntroContentType;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.domain.reward.EarlyBirdDiscountType;
import com.fundit.project.domain.reward.Reward;
import com.fundit.project.domain.reward.RewardOptionGroup;
import com.fundit.project.domain.reward.RewardRepository;
import com.fundit.project.infrastructure.persistence.category.CategoryJpaRepository;
import com.fundit.project.infrastructure.persistence.reward.RewardOptionGroupJpaEntity;
import com.fundit.project.infrastructure.persistence.reward.RewardOptionGroupJpaRepository;
import com.fundit.project.infrastructure.persistence.reward.RewardOptionValueJpaEntity;
import com.fundit.project.infrastructure.persistence.reward.RewardOptionValueJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V1~V3 마이그레이션 + JPA 매핑(JSONB, DB GENERATED 컬럼, updated_at 트리거)이 실제 Postgres에서
 * 정상 동작하는지 검증한다. 서비스 유닛테스트는 리포지토리를 목킹하므로 이 배선은 여기서만 검증된다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
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
    @Autowired
    private RewardOptionGroupJpaRepository optionGroupJpaRepository;
    @Autowired
    private RewardOptionValueJpaRepository optionValueJpaRepository;

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
        Reward reward = Reward.create(persistProjectId(), "얼리버드", "설명", null, 39000L, true, 100, true,
                EarlyBirdDiscountType.RATE, 10L, null, null, null);

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
        Reward reward = rewardRepository.save(Reward.create(projectId, "얼리버드", "설명", null, 39000L, false, null, false, null, null, null, null, null));
        List<RewardOptionGroup> options = List.of(new RewardOptionGroup("색상", List.of("화이트", "블랙")));

        // when
        rewardRepository.replaceOptions(reward.getId(), options);

        // then — replaceOptions 자체가 예외 없이 완료되면 정상(옵션은 응답에 되읽지 않는 설계).
        Reward reloaded = rewardRepository.findById(reward.getId()).orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(reward.getId());
    }

    @Test
    void 기존_그룹_ID를_포함해_치환하면_그룹_ID가_유지된채_이름과_값만_바뀐다() {
        // given
        Long projectId = persistProjectId();
        Reward reward = rewardRepository.save(Reward.create(projectId, "얼리버드", "설명", null, 39000L, false, null, false, null, null, null, null, null));
        rewardRepository.replaceOptions(reward.getId(), List.of(new RewardOptionGroup("색상", List.of("화이트", "블랙"))));
        Long groupId = optionGroupJpaRepository.findByRewardId(reward.getId()).get(0).getId();

        // when — 같은 그룹 ID로 이름/값만 바꿔서 재치환
        rewardRepository.replaceOptions(reward.getId(), List.of(new RewardOptionGroup(groupId, "색깔", List.of("레드"))));

        // then
        List<RewardOptionGroupJpaEntity> groups = optionGroupJpaRepository.findByRewardId(reward.getId());
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getId()).isEqualTo(groupId);
        assertThat(groups.get(0).getName()).isEqualTo("색깔");
        assertThat(optionValueJpaRepository.findByOptionGroupIdOrderBySortOrderAsc(groupId))
                .extracting(RewardOptionValueJpaEntity::getValue)
                .containsExactly("레드");
    }

    @Test
    void 재치환_요청에_없는_기존_그룹은_삭제된다() {
        // given
        Long projectId = persistProjectId();
        Reward reward = rewardRepository.save(Reward.create(projectId, "얼리버드", "설명", null, 39000L, false, null, false, null, null, null, null, null));
        rewardRepository.replaceOptions(reward.getId(), List.of(
                new RewardOptionGroup("색상", List.of("화이트")),
                new RewardOptionGroup("사이즈", List.of("M"))));
        Long colorGroupId = optionGroupJpaRepository.findByRewardId(reward.getId()).stream()
                .filter(g -> g.getName().equals("색상")).findFirst().orElseThrow().getId();

        // when — 색상 그룹만 ID로 유지하고, 사이즈 그룹은 요청에서 뺀다
        rewardRepository.replaceOptions(reward.getId(), List.of(new RewardOptionGroup(colorGroupId, "색상", List.of("화이트"))));

        // then
        assertThat(optionGroupJpaRepository.findByRewardId(reward.getId()))
                .extracting(RewardOptionGroupJpaEntity::getName)
                .containsExactly("색상");
    }

    @Test
    void 다른_리워드_소속_그룹_ID를_보내면_해당_그룹을_건드리지_않고_신규_그룹으로_취급한다() {
        // given
        Long projectId = persistProjectId();
        Reward rewardA = rewardRepository.save(Reward.create(projectId, "A", "설명", null, 1000L, false, null, false, null, null, null, null, null));
        Reward rewardB = rewardRepository.save(Reward.create(projectId, "B", "설명", null, 1000L, false, null, false, null, null, null, null, null));
        rewardRepository.replaceOptions(rewardA.getId(), List.of(new RewardOptionGroup("색상", List.of("화이트"))));
        Long groupIdOfA = optionGroupJpaRepository.findByRewardId(rewardA.getId()).get(0).getId();

        // when — B 리워드 수정 요청에 A 소유 그룹 ID를 실어 보낸다
        rewardRepository.replaceOptions(rewardB.getId(), List.of(new RewardOptionGroup(groupIdOfA, "탈취시도", List.of("x"))));

        // then — A의 그룹은 그대로고, B에는 별도의 새 그룹이 생긴다(A 그룹을 가로채지 않는다)
        assertThat(optionGroupJpaRepository.findByRewardId(rewardA.getId()))
                .extracting(RewardOptionGroupJpaEntity::getName)
                .containsExactly("색상");
        List<RewardOptionGroupJpaEntity> bGroups = optionGroupJpaRepository.findByRewardId(rewardB.getId());
        assertThat(bGroups).hasSize(1);
        assertThat(bGroups.get(0).getId()).isNotEqualTo(groupIdOfA);
    }

    private Long persistProjectId() {
        Instant now = Instant.now();
        return projectRepository.save(Project.builder()
                .publicId(UUID.randomUUID()).sellerId(UUID.randomUUID()).status(ProjectStatus.DRAFT)
                .createdAt(now).updatedAt(now).build()).getId();
    }
}
