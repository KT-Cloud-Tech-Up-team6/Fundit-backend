package com.fundit.project.infrastructure.persistence;

import com.fundit.project.domain.aifundingstory.FundingStoryAnswer;
import com.fundit.project.domain.aifundingstory.FundingStoryImageSource;
import com.fundit.project.domain.aifundingstory.FundingStoryResult;
import com.fundit.project.domain.aifundingstory.FundingStorySection;
import com.fundit.project.domain.aifundingstory.FundingStoryWarning;
import com.fundit.project.domain.fundingstatus.RewardStat;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.infrastructure.persistence.aifundingstory.AiFundingStorySessionJpaEntity;
import com.fundit.project.infrastructure.persistence.aifundingstory.AiFundingStorySessionJpaRepository;
import com.fundit.project.infrastructure.persistence.fundingstatus.FundingStatusSnapshotJpaEntity;
import com.fundit.project.infrastructure.persistence.fundingstatus.FundingStatusSnapshotJpaRepository;
import com.fundit.project.infrastructure.persistence.liveverification.LiveVerificationJpaEntity;
import com.fundit.project.infrastructure.persistence.liveverification.LiveVerificationJpaRepository;
import com.fundit.project.infrastructure.persistence.wishstats.ProjectWishStatJpaEntity;
import com.fundit.project.infrastructure.persistence.wishstats.ProjectWishStatJpaRepository;
import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V4~V6(LIVE검증/펀딩스토리 AI 세션/펀딩현황·찜 통계 읽기모델) 마이그레이션과 JPA 매핑을 검증한다.
 * 특히 ai_funding_story_sessions는 도메인 record(FundingStorySection 등)를 JSONB 컬럼에 직접
 * 매핑하므로(Hibernate 7 @JdbcTypeCode(SqlTypes.JSON)), 중첩 record가 실제로 왕복 직렬화되는지
 * 여기서만 확인 가능하다 — 서비스 유닛테스트는 리포지토리를 목킹해 이 배선을 타지 않는다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ExtendedSlicePersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private LiveVerificationJpaRepository liveVerificationJpaRepository;
    @Autowired
    private AiFundingStorySessionJpaRepository sessionJpaRepository;
    @Autowired
    private FundingStatusSnapshotJpaRepository fundingStatusSnapshotJpaRepository;
    @Autowired
    private ProjectWishStatJpaRepository wishStatJpaRepository;

    private Long persistProjectId() {
        Instant now = Instant.now();
        return projectRepository.save(Project.builder()
                .publicId(UUID.randomUUID()).sellerId(UUID.randomUUID()).status(ProjectStatus.ONGOING)
                .createdAt(now).updatedAt(now).build()).getId();
    }

    @Test
    void LIVE검증_콘텐츠를_저장하고_다시_읽으면_그대로_복원된다() {
        // given
        Long projectId = persistProjectId();

        // when
        LiveVerificationJpaEntity saved = liveVerificationJpaRepository.save(LiveVerificationJpaEntity.builder()
                .projectId(projectId).questionSummaryId("live-q-1").answer("네, 방수 기능 있습니다.").build());
        LiveVerificationJpaEntity reloaded = liveVerificationJpaRepository.findByIdAndDeletedAtIsNull(saved.getId()).orElseThrow();

        // then
        assertThat(reloaded.getAnswer()).isEqualTo("네, 방수 기능 있습니다.");
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    @Test
    void AI_세션의_중첩_JSONB_결과가_그대로_왕복된다() {
        // given
        Long projectId = persistProjectId();
        UUID sellerId = UUID.randomUUID();
        FundingStoryResult result = new FundingStoryResult(
                List.of(new FundingStorySection("INTRO", "제목", "본문", List.of("http://img1.jpg"))),
                List.of(new FundingStoryImageSource("http://img1.jpg", "UPLOADED")),
                List.of(new FundingStoryWarning("body", "근거 없는 주장으로 식별됨")));

        AiFundingStorySessionJpaEntity session = AiFundingStorySessionJpaEntity.builder()
                .id(UuidCreator.getTimeOrderedEpoch())
                .projectId(projectId)
                .sellerId(sellerId)
                .productDescription("캠핑용 프라이팬")
                .productImageUrls(List.of("http://img1.jpg"))
                .answers(List.of(new FundingStoryAnswer("Q1", "캠핑 초보자입니다.")))
                .build();
        session.completeWith(result, List.of());

        // when
        AiFundingStorySessionJpaEntity saved = sessionJpaRepository.save(session);
        AiFundingStorySessionJpaEntity reloaded = sessionJpaRepository.findById(saved.getId()).orElseThrow();

        // then
        assertThat(reloaded.getStatus()).isEqualTo(AiFundingStorySessionJpaEntity.STATUS_COMPLETED);
        assertThat(reloaded.getAnswers()).containsExactly(new FundingStoryAnswer("Q1", "캠핑 초보자입니다."));
        assertThat(reloaded.getResult().sections()).hasSize(1);
        assertThat(reloaded.getResult().sections().get(0).body()).isEqualTo("본문");
        assertThat(reloaded.getResult().warnings().get(0).reason()).isEqualTo("근거 없는 주장으로 식별됨");
    }

    @Test
    void 펀딩현황_스냅샷의_리워드통계_JSONB가_왕복된다() {
        // given
        Long projectId = persistProjectId();
        FundingStatusSnapshotJpaEntity snapshot = FundingStatusSnapshotJpaEntity.builder()
                .projectId(projectId).currentAmount(320000L).achievementRate(64).participantCount(128)
                .rewardStats(List.of(new RewardStat(1L, 30))).lastSyncedAt(Instant.now()).build();

        // when
        fundingStatusSnapshotJpaRepository.save(snapshot);
        FundingStatusSnapshotJpaEntity reloaded = fundingStatusSnapshotJpaRepository.findById(projectId).orElseThrow();

        // then
        assertThat(reloaded.getRewardStats()).containsExactly(new RewardStat(1L, 30));
    }

    @Test
    void 찜_통계를_저장하고_조회한다() {
        // given
        Long projectId = persistProjectId();

        // when
        wishStatJpaRepository.save(ProjectWishStatJpaEntity.builder().projectId(projectId).wishCount(210).build());
        ProjectWishStatJpaEntity reloaded = wishStatJpaRepository.findById(projectId).orElseThrow();

        // then
        assertThat(reloaded.getWishCount()).isEqualTo(210);
    }
}
