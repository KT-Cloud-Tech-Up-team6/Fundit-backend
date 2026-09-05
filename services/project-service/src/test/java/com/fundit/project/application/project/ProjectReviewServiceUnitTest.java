package com.fundit.project.application.project;

import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.infrastructure.persistence.reviewrequest.ProjectReviewRequestJpaEntity;
import com.fundit.project.infrastructure.persistence.reviewrequest.ProjectReviewRequestJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectReviewServiceUnitTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectReviewRequestJpaRepository reviewRequestJpaRepository;

    @InjectMocks
    private ProjectReviewService projectReviewService;

    private Project pendingReviewProject(UUID publicId) {
        return Project.builder()
                .id(1L).publicId(publicId).sellerId(UUID.randomUUID()).status(ProjectStatus.PENDING_REVIEW)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    @Test
    void 승인하면_ONGOING으로_전환되고_펀딩기간이_확정된다() {
        // given
        UUID publicId = UUID.randomUUID();
        Project project = pendingReviewProject(publicId);
        ProjectReviewRequestJpaEntity reviewRequest = ProjectReviewRequestJpaEntity.builder()
                .id(1L).projectId(1L).status(ProjectReviewRequestJpaEntity.STATUS_SUBMITTED)
                .submittedAt(Instant.now()).build();
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));
        when(reviewRequestJpaRepository.findFirstByProjectIdAndStatusOrderBySubmittedAtDesc(1L, ProjectReviewRequestJpaEntity.STATUS_SUBMITTED))
                .thenReturn(Optional.of(reviewRequest));
        when(projectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Project result = projectReviewService.decide(UUID.randomUUID(), publicId, ReviewDecision.APPROVED, null);

        // then
        assertThat(result.getStatus()).isEqualTo(ProjectStatus.ONGOING);
        assertThat(result.getFundingStartAt()).isNotNull();
        assertThat(result.getFundingDeadline()).isNotNull();
        assertThat(reviewRequest.getStatus()).isEqualTo(ProjectReviewRequestJpaEntity.STATUS_APPROVED);
    }

    @Test
    void 반려하면_DRAFT로_되돌아가고_사유가_기록된다() {
        // given
        UUID publicId = UUID.randomUUID();
        Project project = pendingReviewProject(publicId);
        ProjectReviewRequestJpaEntity reviewRequest = ProjectReviewRequestJpaEntity.builder()
                .id(1L).projectId(1L).status(ProjectReviewRequestJpaEntity.STATUS_SUBMITTED)
                .submittedAt(Instant.now()).build();
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));
        when(reviewRequestJpaRepository.findFirstByProjectIdAndStatusOrderBySubmittedAtDesc(1L, ProjectReviewRequestJpaEntity.STATUS_SUBMITTED))
                .thenReturn(Optional.of(reviewRequest));
        when(projectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Project result = projectReviewService.decide(UUID.randomUUID(), publicId, ReviewDecision.REJECTED, "부적합");

        // then
        assertThat(result.getStatus()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(reviewRequest.getStatus()).isEqualTo(ProjectReviewRequestJpaEntity.STATUS_REJECTED);
        assertThat(reviewRequest.getRejectReason()).isEqualTo("부적합");
    }
}
