package com.fundit.project.application.liveverification;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.domain.ProjectErrorCode;
import com.fundit.project.infrastructure.persistence.liveverification.LiveQuestionSummaryJpaEntity;
import com.fundit.project.infrastructure.persistence.liveverification.LiveQuestionSummaryJpaRepository;
import com.fundit.project.infrastructure.persistence.liveverification.LiveVerificationJpaEntity;
import com.fundit.project.infrastructure.persistence.liveverification.LiveVerificationJpaRepository;
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
class LiveVerificationServiceUnitExceptionTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private LiveVerificationJpaRepository liveVerificationJpaRepository;
    @Mock
    private LiveQuestionSummaryJpaRepository liveQuestionSummaryJpaRepository;

    @InjectMocks
    private LiveVerificationService liveVerificationService;

    @Test
    void 존재하지_않는_콘텐츠_수정시_404_예외가_발생한다() {
        // given
        when(liveVerificationJpaRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> liveVerificationService.update(UUID.randomUUID(), 99L, "답변"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 타인_소유_콘텐츠_수정시_403_예외가_발생한다() {
        // given
        LiveVerificationJpaEntity entity = LiveVerificationJpaEntity.builder()
                .id(301L).projectId(1L).questionSummaryId("live-q-1").answer("답변").build();
        Project project = Project.builder()
                .id(1L).publicId(UUID.randomUUID()).sellerId(UUID.randomUUID()).status(ProjectStatus.ONGOING)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(liveVerificationJpaRepository.findByIdAndDeletedAtIsNull(301L)).thenReturn(Optional.of(entity));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        // when & then
        assertThatThrownBy(() -> liveVerificationService.update(UUID.randomUUID(), 301L, "답변"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.FORBIDDEN);
    }

    @Test
    void 비공개_프로젝트의_LIVE검증_목록조회는_404를_반환한다() {
        // given
        UUID publicId = UUID.randomUUID();
        Project project = Project.builder()
                .id(1L).publicId(publicId).sellerId(UUID.randomUUID()).status(ProjectStatus.DRAFT)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));

        // when & then
        assertThatThrownBy(() -> liveVerificationService.listForConsumer(publicId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 수신된_적_없는_질문에_답변하면_404_예외가_발생한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        Project project = Project.builder()
                .id(1L).publicId(publicId).sellerId(sellerId).status(ProjectStatus.ONGOING)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));
        when(liveQuestionSummaryJpaRepository.findByProjectIdAndQuestionSummaryId(1L, "live-q-x"))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> liveVerificationService.create(sellerId, publicId, "live-q-x", "답변"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.LIVE_QUESTION_SUMMARY_NOT_FOUND);
    }

    @Test
    void 같은_질문에_두번_답변하면_409_예외가_발생한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        Project project = Project.builder()
                .id(1L).publicId(publicId).sellerId(sellerId).status(ProjectStatus.ONGOING)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));
        when(liveQuestionSummaryJpaRepository.findByProjectIdAndQuestionSummaryId(1L, "live-q-1"))
                .thenReturn(Optional.of(LiveQuestionSummaryJpaEntity.builder()
                        .id(1L).projectId(1L).questionSummaryId("live-q-1").summaryText("질문").questionCount(3).build()));
        when(liveVerificationJpaRepository.existsByProjectIdAndQuestionSummaryIdAndDeletedAtIsNull(1L, "live-q-1"))
                .thenReturn(true);

        // when & then
        assertThatThrownBy(() -> liveVerificationService.create(sellerId, publicId, "live-q-1", "답변"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.LIVE_VERIFICATION_ALREADY_EXISTS);
    }

    @Test
    void 타인_프로젝트의_질문목록을_조회하면_403_예외가_발생한다() {
        // given
        UUID publicId = UUID.randomUUID();
        Project project = Project.builder()
                .id(1L).publicId(publicId).sellerId(UUID.randomUUID()).status(ProjectStatus.ONGOING)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));

        // when & then
        assertThatThrownBy(() -> liveVerificationService.listQuestionsForSeller(UUID.randomUUID(), publicId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.FORBIDDEN);
    }
}
