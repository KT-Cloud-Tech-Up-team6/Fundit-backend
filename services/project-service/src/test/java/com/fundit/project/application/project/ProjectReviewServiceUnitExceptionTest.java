package com.fundit.project.application.project;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.domain.ProjectErrorCode;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectReviewServiceUnitExceptionTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectReviewRequestJpaRepository reviewRequestJpaRepository;

    @InjectMocks
    private ProjectReviewService projectReviewService;

    @Test
    void 존재하지_않는_프로젝트면_404_예외가_발생한다() {
        // given
        UUID publicId = UUID.randomUUID();
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> projectReviewService.decide(UUID.randomUUID(), publicId, ReviewDecision.APPROVED, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 심사대기중이_아니면_예외가_발생한다() {
        // given
        UUID publicId = UUID.randomUUID();
        Project project = Project.builder()
                .id(1L).publicId(publicId).sellerId(UUID.randomUUID()).status(ProjectStatus.DRAFT)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));
        when(reviewRequestJpaRepository.findFirstByProjectIdAndStatusOrderBySubmittedAtDesc(1L, ProjectReviewRequestJpaEntity.STATUS_SUBMITTED))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> projectReviewService.decide(UUID.randomUUID(), publicId, ReviewDecision.APPROVED, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.PROJECT_NOT_REVIEWABLE);
    }

    @Test
    void 반려_사유가_없으면_예외가_발생한다() {
        // given
        UUID publicId = UUID.randomUUID();
        Project project = Project.builder()
                .id(1L).publicId(publicId).sellerId(UUID.randomUUID()).status(ProjectStatus.PENDING_REVIEW)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        ProjectReviewRequestJpaEntity reviewRequest = ProjectReviewRequestJpaEntity.builder()
                .id(1L).projectId(1L).status(ProjectReviewRequestJpaEntity.STATUS_SUBMITTED)
                .submittedAt(Instant.now()).build();
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));
        when(reviewRequestJpaRepository.findFirstByProjectIdAndStatusOrderBySubmittedAtDesc(1L, ProjectReviewRequestJpaEntity.STATUS_SUBMITTED))
                .thenReturn(Optional.of(reviewRequest));

        // when & then
        assertThatThrownBy(() -> projectReviewService.decide(UUID.randomUUID(), publicId, ReviewDecision.REJECTED, "  "))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_INPUT);
    }
}
