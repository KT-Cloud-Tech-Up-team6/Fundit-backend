package com.fundit.project.application.project;

import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.infrastructure.persistence.category.CategoryJpaRepository;
import com.fundit.project.infrastructure.persistence.privacyconsent.ProjectPrivacyConsentJpaRepository;
import com.fundit.project.infrastructure.persistence.project.ProjectJpaRepository;
import com.fundit.project.infrastructure.persistence.reviewrequest.ProjectReviewRequestJpaEntity;
import com.fundit.project.infrastructure.persistence.reviewrequest.ProjectReviewRequestJpaRepository;
import com.fundit.project.infrastructure.persistence.reward.RewardJpaRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceUnitTest {

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
    void 신규_프로젝트를_DRAFT_상태로_생성한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        when(projectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Project result = projectService.create(sellerId);

        // then
        assertThat(result.getSellerId()).isEqualTo(sellerId);
        assertThat(result.getStatus()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(result.getPublicId()).isNotNull();
    }

    @Nested
    class 삭제 {

        @Test
        void 본인_소유_DRAFT_프로젝트는_삭제된다() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID publicId = UUID.randomUUID();
            Project project = ownedDraftProject(sellerId, publicId);
            when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));
            when(projectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            projectService.delete(sellerId, publicId);

            // then
            ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
            verify(projectRepository).save(captor.capture());
            assertThat(captor.getValue().isDeleted()).isTrue();
        }
    }

    @Nested
    class 기본정보_수정 {

        @Test
        void 카테고리_조합이_존재하면_기본정보가_저장된다() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID publicId = UUID.randomUUID();
            Project project = ownedDraftProject(sellerId, publicId);
            when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));
            when(categoryJpaRepository.existsByCategoryMajorAndCategoryMinor("테크·가전", "생활가전")).thenReturn(true);
            when(projectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            Project result = projectService.updateBasicInfo(sellerId, publicId,
                    new ProjectService.UpdateBasicInfoCommand("SOLE", "테크·가전", "생활가전", "제목", 1_000_000L));

            // then
            assertThat(result.getTitle()).isEqualTo("제목");
            assertThat(result.getCategoryMajor()).isEqualTo("테크·가전");
        }
    }

    @Nested
    class 개인정보_동의 {

        @Test
        void agreed가_true면_동의이력이_저장된다() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID publicId = UUID.randomUUID();
            Project project = ownedDraftProject(sellerId, publicId);
            when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));

            // when
            Instant consentedAt = projectService.consentPrivacy(sellerId, publicId, true);

            // then
            assertThat(consentedAt).isNotNull();
            verify(privacyConsentJpaRepository).save(any());
        }
    }

    @Nested
    class 심사_제출 {

        @Test
        void 필수항목이_모두_완료되면_PENDING_REVIEW로_전환된다() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID publicId = UUID.randomUUID();
            Project project = ownedDraftProject(sellerId, publicId).toBuilder()
                    .businessType(com.fundit.project.domain.project.BusinessType.SOLE)
                    .categoryMajor("테크·가전").categoryMinor("생활가전")
                    .title("제목").goalAmount(1_000_000L)
                    .introContent(java.util.List.of(new com.fundit.project.domain.project.IntroContentBlock(
                            com.fundit.project.domain.project.IntroContentType.TEXT, "본문")))
                    .build();
            when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));
            when(rewardJpaRepository.existsByProjectIdAndDeletedAtIsNull(1L)).thenReturn(true);
            when(privacyConsentJpaRepository.existsByProjectIdAndAgreedTrue(1L)).thenReturn(true);
            when(projectRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            Project result = projectService.submit(sellerId, publicId);

            // then
            assertThat(result.getStatus()).isEqualTo(ProjectStatus.PENDING_REVIEW);
            verify(reviewRequestJpaRepository).save(any(ProjectReviewRequestJpaEntity.class));
        }
    }
}
