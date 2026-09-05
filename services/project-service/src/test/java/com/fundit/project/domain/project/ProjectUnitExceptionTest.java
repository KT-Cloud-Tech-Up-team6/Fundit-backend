package com.fundit.project.domain.project;

import com.fundit.common.error.BusinessException;
import com.fundit.project.domain.ProjectErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectUnitExceptionTest {

    private Project draftProject() {
        return Project.builder()
                .id(1L)
                .publicId(UUID.randomUUID())
                .sellerId(UUID.randomUUID())
                .status(ProjectStatus.DRAFT)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void 목표금액이_50만원_미만이면_예외가_발생한다() {
        // given
        Project project = draftProject();

        // when & then
        assertThatThrownBy(() -> project.updateBasicInfo(null, null, null, null, 499_999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.GOAL_AMOUNT_TOO_LOW);
    }

    @Test
    void DRAFT가_아니면_삭제할_수_없다() {
        // given
        Project project = draftProject().toBuilder().status(ProjectStatus.ONGOING).build();

        // when & then
        assertThatThrownBy(project::delete)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.PROJECT_NOT_DELETABLE);
    }

    @Test
    void DRAFT가_아니면_제출할_수_없다() {
        // given
        Project project = draftProject().toBuilder().status(ProjectStatus.PENDING_REVIEW).build();

        // when & then
        assertThatThrownBy(project::submit)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.PROJECT_NOT_SUBMITTABLE);
    }

    @Test
    void PENDING_REVIEW가_아니면_승인할_수_없다() {
        // given
        Project project = draftProject();

        // when & then
        assertThatThrownBy(() -> project.approve(Instant.now(), Instant.now().plusSeconds(60)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.PROJECT_NOT_REVIEWABLE);
    }

    @Test
    void PENDING_REVIEW가_아니면_반려할_수_없다() {
        // given
        Project project = draftProject();

        // when & then
        assertThatThrownBy(project::reject)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.PROJECT_NOT_REVIEWABLE);
    }
}
