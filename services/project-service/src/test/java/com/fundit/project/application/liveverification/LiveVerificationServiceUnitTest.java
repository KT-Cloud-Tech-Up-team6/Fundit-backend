package com.fundit.project.application.liveverification;

import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.infrastructure.persistence.liveverification.LiveQuestionSummaryJpaEntity;
import com.fundit.project.infrastructure.persistence.liveverification.LiveQuestionSummaryJpaRepository;
import com.fundit.project.infrastructure.persistence.liveverification.LiveVerificationJpaEntity;
import com.fundit.project.infrastructure.persistence.liveverification.LiveVerificationJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class LiveVerificationServiceUnitTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private LiveVerificationJpaRepository liveVerificationJpaRepository;
    @Mock
    private LiveQuestionSummaryJpaRepository liveQuestionSummaryJpaRepository;

    @InjectMocks
    private LiveVerificationService liveVerificationService;

    private LiveQuestionSummaryJpaEntity summary(String questionSummaryId, String text, int count) {
        return LiveQuestionSummaryJpaEntity.builder()
                .id(1L).projectId(1L).questionSummaryId(questionSummaryId).summaryText(text).questionCount(count).build();
    }

    private Project ownedProject(UUID sellerId, UUID publicId, ProjectStatus status) {
        return Project.builder()
                .id(1L).publicId(publicId).sellerId(sellerId).status(status)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    @Test
    void 본인_프로젝트에_LIVE검증_콘텐츠를_등록한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        Project project = ownedProject(sellerId, publicId, ProjectStatus.ONGOING);
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));
        when(liveQuestionSummaryJpaRepository.findByProjectIdAndQuestionSummaryId(1L, "live-q-1"))
                .thenReturn(Optional.of(summary("live-q-1", "배송은 얼마나 걸리나요?", 12)));
        when(liveVerificationJpaRepository.existsByProjectIdAndQuestionSummaryIdAndDeletedAtIsNull(1L, "live-q-1"))
                .thenReturn(false);
        when(liveVerificationJpaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        LiveVerificationJpaEntity result = liveVerificationService.create(sellerId, publicId, "live-q-1", "네, 있습니다.");

        // then
        assertThat(result.getQuestionSummaryId()).isEqualTo("live-q-1");
        assertThat(result.getAnswer()).isEqualTo("네, 있습니다.");
    }

    @Test
    void 답변을_수정한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        LiveVerificationJpaEntity entity = LiveVerificationJpaEntity.builder()
                .id(301L).projectId(1L).questionSummaryId("live-q-1").answer("기존답변").build();
        Project project = ownedProject(sellerId, UUID.randomUUID(), ProjectStatus.ONGOING);
        when(liveVerificationJpaRepository.findByIdAndDeletedAtIsNull(301L)).thenReturn(Optional.of(entity));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(liveVerificationJpaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        LiveVerificationJpaEntity result = liveVerificationService.update(sellerId, 301L, "수정된답변");

        // then
        assertThat(result.getAnswer()).isEqualTo("수정된답변");
    }

    @Test
    void 삭제하면_소프트삭제된다() {
        // given
        UUID sellerId = UUID.randomUUID();
        LiveVerificationJpaEntity entity = LiveVerificationJpaEntity.builder()
                .id(301L).projectId(1L).questionSummaryId("live-q-1").answer("답변").build();
        Project project = ownedProject(sellerId, UUID.randomUUID(), ProjectStatus.ONGOING);
        when(liveVerificationJpaRepository.findByIdAndDeletedAtIsNull(301L)).thenReturn(Optional.of(entity));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(liveVerificationJpaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        liveVerificationService.delete(sellerId, 301L);

        // then
        ArgumentCaptor<LiveVerificationJpaEntity> captor = ArgumentCaptor.forClass(LiveVerificationJpaEntity.class);
        verify(liveVerificationJpaRepository).save(captor.capture());
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
    }

    @Test
    void 공개_프로젝트의_LIVE검증_목록은_질문문구와_실제건수를_함께_내려준다() {
        // given
        UUID publicId = UUID.randomUUID();
        Project project = ownedProject(UUID.randomUUID(), publicId, ProjectStatus.ONGOING);
        LiveVerificationJpaEntity entity = LiveVerificationJpaEntity.builder()
                .id(301L).projectId(1L).questionSummaryId("live-q-1").answer("답변").build();
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));
        when(liveQuestionSummaryJpaRepository.findByProjectIdOrderByQuestionCountDesc(1L))
                .thenReturn(List.of(summary("live-q-1", "배송은 얼마나 걸리나요?", 12)));
        when(liveVerificationJpaRepository.findByProjectIdAndDeletedAtIsNull(1L)).thenReturn(List.of(entity));

        // when
        List<LiveQuestionAnswerView> result = liveVerificationService.listForConsumer(publicId);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).questionText()).isEqualTo("배송은 얼마나 걸리나요?");
        assertThat(result.get(0).questionCount()).isEqualTo(12);
        assertThat(result.get(0).answer()).isEqualTo("답변");
    }

    @Test
    void 판매자_질문목록은_미답변_질문도_포함한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        Project project = ownedProject(sellerId, publicId, ProjectStatus.ONGOING);
        LiveVerificationJpaEntity answered = LiveVerificationJpaEntity.builder()
                .id(301L).projectId(1L).questionSummaryId("live-q-1").answer("답변").build();
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));
        when(liveVerificationJpaRepository.findByProjectIdAndDeletedAtIsNull(1L)).thenReturn(List.of(answered));
        when(liveQuestionSummaryJpaRepository.findByProjectIdOrderByQuestionCountDesc(1L)).thenReturn(List.of(
                summary("live-q-1", "배송은 얼마나 걸리나요?", 12),
                summary("live-q-2", "방수 되나요?", 5)));

        // when
        List<LiveQuestionAnswerView> result = liveVerificationService.listQuestionsForSeller(sellerId, publicId);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).answered()).isTrue();
        assertThat(result.get(1).answered()).isFalse();
        assertThat(result.get(1).liveVerificationId()).isNull();
    }

    @Test
    void 질문요약_이벤트를_받으면_질문을_저장한다() {
        // given
        UUID publicId = UUID.randomUUID();
        Project project = ownedProject(UUID.randomUUID(), publicId, ProjectStatus.ONGOING);
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));
        when(liveQuestionSummaryJpaRepository.findByProjectIdAndQuestionSummaryId(1L, "live-q-1"))
                .thenReturn(Optional.empty());

        // when
        liveVerificationService.applySummaries(publicId, List.of(
                new LiveQuestionsSummarizedEvent.Summary("live-q-1", "배송은 얼마나 걸리나요?", 12)));

        // then
        ArgumentCaptor<LiveQuestionSummaryJpaEntity> captor = ArgumentCaptor.forClass(LiveQuestionSummaryJpaEntity.class);
        verify(liveQuestionSummaryJpaRepository).save(captor.capture());
        assertThat(captor.getValue().getSummaryText()).isEqualTo("배송은 얼마나 걸리나요?");
        assertThat(captor.getValue().getQuestionCount()).isEqualTo(12);
    }

    @Test
    void 같은_질문요약_이벤트를_다시_받아도_행이_늘지_않고_건수만_갱신된다() {
        // given
        UUID publicId = UUID.randomUUID();
        Project project = ownedProject(UUID.randomUUID(), publicId, ProjectStatus.ONGOING);
        LiveQuestionSummaryJpaEntity existing = summary("live-q-1", "배송은 얼마나 걸리나요?", 12);
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));
        when(liveQuestionSummaryJpaRepository.findByProjectIdAndQuestionSummaryId(1L, "live-q-1"))
                .thenReturn(Optional.of(existing));

        // when
        liveVerificationService.applySummaries(publicId, List.of(
                new LiveQuestionsSummarizedEvent.Summary("live-q-1", "배송은 얼마나 걸리나요?", 20)));

        // then
        verify(liveQuestionSummaryJpaRepository, never()).save(any());
        assertThat(existing.getQuestionCount()).isEqualTo(20);
    }

    @Test
    void 알수없는_프로젝트의_질문요약_이벤트는_무시한다() {
        // given
        UUID publicId = UUID.randomUUID();
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.empty());

        // when
        liveVerificationService.applySummaries(publicId, List.of(
                new LiveQuestionsSummarizedEvent.Summary("live-q-1", "질문", 1)));

        // then
        verify(liveQuestionSummaryJpaRepository, never()).save(any());
    }
}
