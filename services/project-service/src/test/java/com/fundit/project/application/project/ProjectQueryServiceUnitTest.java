package com.fundit.project.application.project;

import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.infrastructure.persistence.fundingstatus.FundingStatusSnapshotJpaEntity;
import com.fundit.project.infrastructure.persistence.fundingstatus.FundingStatusSnapshotJpaRepository;
import com.fundit.project.infrastructure.persistence.liveverification.LiveVerificationJpaRepository;
import com.fundit.project.infrastructure.persistence.reward.RewardJpaEntity;
import com.fundit.project.infrastructure.persistence.reward.RewardJpaRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectQueryServiceUnitTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private FundingStatusSnapshotJpaRepository fundingStatusSnapshotJpaRepository;
    @Mock
    private LiveVerificationJpaRepository liveVerificationJpaRepository;
    @Mock
    private RewardJpaRepository rewardJpaRepository;
    @Mock
    private SellerProfileClient sellerProfileClient;

    @InjectMocks
    private ProjectQueryService projectQueryService;

    private Project project(UUID sellerId, UUID publicId, ProjectStatus status) {
        return Project.builder()
                .id(1L).publicId(publicId).sellerId(sellerId).status(status)
                .title("제목").goalAmount(1_000_000L)
                .fundingDeadline(Instant.now().plusSeconds(5 * 24 * 3600))
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    @Test
    void 본인은_비공개_프로젝트도_미리보기로_조회할_수_있다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        Project draft = project(sellerId, publicId, ProjectStatus.DRAFT);
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(draft));
        when(fundingStatusSnapshotJpaRepository.findById(1L)).thenReturn(Optional.empty());
        when(liveVerificationJpaRepository.existsByProjectIdAndDeletedAtIsNull(1L)).thenReturn(false);
        when(sellerProfileClient.getDisplayName(sellerId)).thenReturn(Optional.empty());

        // when
        var result = projectQueryService.getPreview(sellerId, publicId);

        // then
        assertThat(result.title()).isEqualTo("제목");
        assertThat(result.status()).isEqualTo("DRAFT");
    }

    @Test
    void 공개_프로젝트_상세는_펀딩현황_스냅샷을_반영한다() {
        // given
        UUID publicId = UUID.randomUUID();
        Project ongoing = project(UUID.randomUUID(), publicId, ProjectStatus.ONGOING);
        FundingStatusSnapshotJpaEntity snapshot = FundingStatusSnapshotJpaEntity.builder()
                .projectId(1L).currentAmount(320000L).achievementRate(64).participantCount(128).build();
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(ongoing));
        when(fundingStatusSnapshotJpaRepository.findById(1L)).thenReturn(Optional.of(snapshot));
        when(liveVerificationJpaRepository.existsByProjectIdAndDeletedAtIsNull(1L)).thenReturn(true);
        when(sellerProfileClient.getDisplayName(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());

        // when
        var result = projectQueryService.getPublicDetail(publicId);

        // then
        assertThat(result.fundingStatus().currentAmount()).isEqualTo(320000L);
        assertThat(result.hasLiveVerification()).isTrue();
    }

    @Test
    void 환불정책은_공통정책과_리워드별_정책을_함께_반환한다() {
        // given
        UUID publicId = UUID.randomUUID();
        Project ongoing = project(UUID.randomUUID(), publicId, ProjectStatus.ONGOING);
        RewardJpaEntity reward = RewardJpaEntity.builder()
                .id(1L).projectId(1L).name("얼리버드").description("설명").price(39000L)
                .isLimited(false).isEarlyBird(false).hasOption(false).sortOrder(0)
                .simpleRefundDisabled(true).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(ongoing));
        when(rewardJpaRepository.findByProjectIdAndDeletedAtIsNullOrderBySortOrderAsc(1L)).thenReturn(List.of(reward));

        // when
        var result = projectQueryService.getRefundPolicy(publicId);

        // then
        assertThat(result.commonPolicy().goalFailedAutoRefund()).isTrue();
        assertThat(result.rewardPolicies().get(0).simpleRefundDisabled()).isTrue();
    }
}
