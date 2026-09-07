package com.fundit.project.application.project;

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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
        return Project.builder()
                .id(1L).publicId(publicId).sellerId(sellerId).status(ProjectStatus.ONGOING)
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
}
