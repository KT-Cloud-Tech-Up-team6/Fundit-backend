package com.fundit.project.application.project;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.domain.ProjectErrorCode;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.infrastructure.persistence.category.CategoryJpaRepository;
import com.fundit.project.infrastructure.persistence.privacyconsent.ProjectPrivacyConsentJpaRepository;
import com.fundit.project.infrastructure.persistence.project.ProjectJpaRepository;
import com.fundit.project.infrastructure.persistence.reviewrequest.ProjectReviewRequestJpaRepository;
import com.fundit.project.infrastructure.persistence.reward.RewardJpaRepository;
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
class ProjectServiceUnitExceptionTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectJpaRepository projectJpaRepository;
    @Mock
    private CategoryJpaRepository categoryJpaRepository;
    @Mock
    private ProjectPrivacyConsentJpaRepository privacyConsentJpaRepository;
    @Mock
    private ProjectReviewRequestJpaRepository reviewRequestJpaRepository;
    @Mock
    private RewardJpaRepository rewardJpaRepository;

    @InjectMocks
    private ProjectService projectService;

    private Project ownedDraftProject(UUID sellerId, UUID publicId) {
        return Project.builder()
                .id(1L).publicId(publicId).sellerId(sellerId).status(ProjectStatus.DRAFT)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    @Test
    void 존재하지_않는_프로젝트를_삭제하면_404_예외가_발생한다() {
        // given
        UUID publicId = UUID.randomUUID();
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> projectService.delete(UUID.randomUUID(), publicId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 타인_소유_프로젝트를_삭제하면_403_예외가_발생한다() {
        // given
        UUID publicId = UUID.randomUUID();
        Project project = ownedDraftProject(UUID.randomUUID(), publicId);
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));

        // when & then
        assertThatThrownBy(() -> projectService.delete(UUID.randomUUID(), publicId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.FORBIDDEN);
    }

    @Test
    void 존재하지_않는_카테고리로_기본정보를_수정하면_예외가_발생한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        Project project = ownedDraftProject(sellerId, publicId);
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));
        when(categoryJpaRepository.existsByCategoryMajorAndCategoryMinor("없는대분류", "없는중분류")).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> projectService.updateBasicInfo(sellerId, publicId,
                new ProjectService.UpdateBasicInfoCommand(null, "없는대분류", "없는중분류", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.INVALID_CATEGORY);
    }

    @Test
    void 개인정보_동의를_거부하면_예외가_발생한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        Project project = ownedDraftProject(sellerId, publicId);
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));

        // when & then
        assertThatThrownBy(() -> projectService.consentPrivacy(sellerId, publicId, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.PRIVACY_CONSENT_REQUIRED);
    }

    @Test
    void 필수항목이_미완료면_제출할_수_없다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        Project project = ownedDraftProject(sellerId, publicId);
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));

        // when & then
        assertThatThrownBy(() -> projectService.submit(sellerId, publicId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.PROJECT_NOT_SUBMITTABLE);
    }
}
