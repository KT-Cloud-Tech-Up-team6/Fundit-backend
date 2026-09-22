package com.fundit.project.application.project;

import com.fundit.project.domain.fundingstatus.RewardStat;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.infrastructure.persistence.fundingstatus.FundingStatusSnapshotJpaEntity;
import com.fundit.project.infrastructure.persistence.fundingstatus.FundingStatusSnapshotJpaRepository;
import com.fundit.project.infrastructure.persistence.opennotify.ProjectOpenNotifyRequestJpaRepository;
import com.fundit.project.infrastructure.persistence.wishstats.ProjectWishStatJpaEntity;
import com.fundit.project.infrastructure.persistence.wishstats.ProjectWishStatJpaRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectStatsServiceUnitTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private FundingStatusSnapshotJpaRepository fundingStatusSnapshotJpaRepository;
    @Mock
    private ProjectWishStatJpaRepository wishStatJpaRepository;
    @Mock
    private ProjectOpenNotifyRequestJpaRepository openNotifyRequestJpaRepository;

    @InjectMocks
    private ProjectStatsService projectStatsService;

    private Project ownedProject(UUID sellerId, UUID publicId) {
        return ownedProject(sellerId, publicId, 1_000_000L);
    }

    private Project ownedProject(UUID sellerId, UUID publicId, Long goalAmount) {
        return Project.builder()
                .id(1L).publicId(publicId).sellerId(sellerId).status(ProjectStatus.ONGOING)
                .goalAmount(goalAmount)
                .fundingDeadline(Instant.now().plusSeconds(5 * 24 * 3600))
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    @Test
    void 스냅샷이_없으면_기본값_0으로_조회된다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(ownedProject(sellerId, publicId)));
        when(fundingStatusSnapshotJpaRepository.findById(1L)).thenReturn(Optional.empty());
        when(wishStatJpaRepository.findById(1L)).thenReturn(Optional.empty());
        when(openNotifyRequestJpaRepository.countByProjectId(1L)).thenReturn(0L);

        // when
        var result = projectStatsService.getFundingStatus(sellerId, publicId);

        // then
        assertThat(result.currentAmount()).isZero();
        assertThat(result.wishCount()).isZero();
    }

    @Test
    void 스냅샷이_있으면_그대로_반영된다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        FundingStatusSnapshotJpaEntity snapshot = FundingStatusSnapshotJpaEntity.builder()
                .projectId(1L).currentAmount(320000L).achievementRate(64).participantCount(128).build();
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(ownedProject(sellerId, publicId)));
        when(fundingStatusSnapshotJpaRepository.findById(1L)).thenReturn(Optional.of(snapshot));
        when(wishStatJpaRepository.findById(1L)).thenReturn(Optional.of(ProjectWishStatJpaEntity.builder().projectId(1L).wishCount(210).build()));
        when(openNotifyRequestJpaRepository.countByProjectId(1L)).thenReturn(40L);

        // when
        var result = projectStatsService.getFundingStatus(sellerId, publicId);

        // then
        assertThat(result.currentAmount()).isEqualTo(320000L);
        assertThat(result.wishCount()).isEqualTo(210);
        assertThat(result.openNotifyCount()).isEqualTo(40L);
    }

    @Test
    void 찜통계를_조회한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(ownedProject(sellerId, publicId)));
        when(wishStatJpaRepository.findById(1L)).thenReturn(Optional.of(ProjectWishStatJpaEntity.builder().projectId(1L).wishCount(210).build()));
        when(openNotifyRequestJpaRepository.countByProjectId(1L)).thenReturn(40L);

        // when
        var result = projectStatsService.getWishStats(sellerId, publicId);

        // then
        assertThat(result.wishCount()).isEqualTo(210);
        assertThat(result.openNotifyCount()).isEqualTo(40L);
    }

    @Nested
    class 찜_이벤트 {

        @Test
        void 처음_찜하면_카운트를_올린다() {
            // given
            UUID memberId = UUID.randomUUID();
            when(wishStatJpaRepository.insertMemberIfAbsent(1L, memberId)).thenReturn(1);

            // when
            projectStatsService.applyProjectWished(1L, memberId);

            // then
            verify(wishStatJpaRepository).incrementOrCreate(1L);
        }

        @Test
        void 같은_회원_찜_이벤트는_카운트를_다시_올리지_않는다() {
            // given
            UUID memberId = UUID.randomUUID();
            when(wishStatJpaRepository.insertMemberIfAbsent(1L, memberId)).thenReturn(0);

            // when
            projectStatsService.applyProjectWished(1L, memberId);

            // then
            verify(wishStatJpaRepository, never()).incrementOrCreate(1L);
        }

        @Test
        void 찜을_해제하면_카운트를_내린다() {
            // given
            UUID memberId = UUID.randomUUID();
            when(wishStatJpaRepository.deleteMember(1L, memberId)).thenReturn(1);

            // when
            projectStatsService.applyProjectUnwished(1L, memberId);

            // then
            verify(wishStatJpaRepository).decrementIfPresent(1L);
        }

        @Test
        void 이미_해제된_찜_이벤트는_카운트를_내리지_않는다() {
            // given
            UUID memberId = UUID.randomUUID();
            when(wishStatJpaRepository.deleteMember(1L, memberId)).thenReturn(0);

            // when
            projectStatsService.applyProjectUnwished(1L, memberId);

            // then
            verify(wishStatJpaRepository, never()).decrementIfPresent(1L);
        }
    }

    @Test
    void 리워드_통계_이벤트를_받으면_스냅샷을_교체한다() {
        // given
        UUID publicId = UUID.randomUUID();
        List<RewardStat> stats = List.of(new RewardStat(1L, 100L, 2, 20_000L));
        when(projectRepository.findByPublicId(publicId))
                .thenReturn(Optional.of(ownedProject(UUID.randomUUID(), publicId)));
        when(fundingStatusSnapshotJpaRepository.findById(1L)).thenReturn(Optional.empty());
        when(fundingStatusSnapshotJpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        projectStatsService.applyRewardStats(publicId, stats);

        // then
        var captor = org.mockito.ArgumentCaptor.forClass(FundingStatusSnapshotJpaEntity.class);
        verify(fundingStatusSnapshotJpaRepository).save(captor.capture());
        assertThat(captor.getValue().getRewardStats()).isEqualTo(stats);
        assertThat(captor.getValue().getLastSyncedAt()).isNotNull();
    }

    @Test
    void 리워드_통계로_모금액과_달성률을_계산한다() {
        // given — 목표 100만원, 리워드 단위 합 30만원
        UUID publicId = UUID.randomUUID();
        List<RewardStat> stats = List.of(
                new RewardStat(1L, null, 2, 200_000L),
                new RewardStat(2L, null, 1, 100_000L));
        when(projectRepository.findByPublicId(publicId))
                .thenReturn(Optional.of(ownedProject(UUID.randomUUID(), publicId, 1_000_000L)));
        when(fundingStatusSnapshotJpaRepository.findById(1L)).thenReturn(Optional.empty());
        when(fundingStatusSnapshotJpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        projectStatsService.applyRewardStats(publicId, stats);

        // then
        var captor = org.mockito.ArgumentCaptor.forClass(FundingStatusSnapshotJpaEntity.class);
        verify(fundingStatusSnapshotJpaRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentAmount()).isEqualTo(300_000L);
        assertThat(captor.getValue().getAchievementRate()).isEqualTo(30);
    }

    @Test
    void 옵션_단위_행은_모금액에서_제외한다() {
        // given — 옵션 행은 리워드 행의 금액을 쪼갠 것이라 같이 더하면 중복 계상된다
        UUID publicId = UUID.randomUUID();
        List<RewardStat> stats = List.of(
                new RewardStat(1L, null, 2, 200_000L),
                new RewardStat(1L, 100L, 1, 100_000L),
                new RewardStat(1L, 101L, 1, 100_000L));
        when(projectRepository.findByPublicId(publicId))
                .thenReturn(Optional.of(ownedProject(UUID.randomUUID(), publicId, 1_000_000L)));
        when(fundingStatusSnapshotJpaRepository.findById(1L)).thenReturn(Optional.empty());
        when(fundingStatusSnapshotJpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        projectStatsService.applyRewardStats(publicId, stats);

        // then
        var captor = org.mockito.ArgumentCaptor.forClass(FundingStatusSnapshotJpaEntity.class);
        verify(fundingStatusSnapshotJpaRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentAmount()).isEqualTo(200_000L);
    }

    @Test
    void 목표금액이_없으면_달성률은_0이다() {
        // given — DRAFT 단계는 goal_amount가 NULL이다
        UUID publicId = UUID.randomUUID();
        when(projectRepository.findByPublicId(publicId))
                .thenReturn(Optional.of(ownedProject(UUID.randomUUID(), publicId, null)));
        when(fundingStatusSnapshotJpaRepository.findById(1L)).thenReturn(Optional.empty());
        when(fundingStatusSnapshotJpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        projectStatsService.applyRewardStats(publicId, List.of(new RewardStat(1L, null, 2, 200_000L)));

        // then
        var captor = org.mockito.ArgumentCaptor.forClass(FundingStatusSnapshotJpaEntity.class);
        verify(fundingStatusSnapshotJpaRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentAmount()).isEqualTo(200_000L);
        assertThat(captor.getValue().getAchievementRate()).isZero();
    }

    @Test
    void 알수없는_projectPublicId면_스냅샷_저장없이_건너뛴다() {
        // given
        UUID publicId = UUID.randomUUID();
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.empty());

        // when
        projectStatsService.applyRewardStats(publicId, List.of(new RewardStat(1L, 100L, 2, 20_000L)));

        // then
        verify(fundingStatusSnapshotJpaRepository, never()).save(any());
    }
}
