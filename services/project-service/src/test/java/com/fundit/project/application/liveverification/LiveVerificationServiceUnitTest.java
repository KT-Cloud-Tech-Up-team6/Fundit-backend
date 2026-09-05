package com.fundit.project.application.liveverification;

import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiveVerificationServiceUnitTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private LiveVerificationJpaRepository liveVerificationJpaRepository;

    @InjectMocks
    private LiveVerificationService liveVerificationService;

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
    void 공개_프로젝트의_LIVE검증_목록을_조회한다() {
        // given
        UUID publicId = UUID.randomUUID();
        Project project = ownedProject(UUID.randomUUID(), publicId, ProjectStatus.ONGOING);
        LiveVerificationJpaEntity entity = LiveVerificationJpaEntity.builder()
                .id(301L).projectId(1L).questionSummaryId("live-q-1").answer("답변").questionCount(12).build();
        when(projectRepository.findByPublicId(publicId)).thenReturn(Optional.of(project));
        when(liveVerificationJpaRepository.findByProjectIdAndDeletedAtIsNull(1L)).thenReturn(List.of(entity));

        // when
        List<LiveVerificationJpaEntity> result = liveVerificationService.listForConsumer(publicId);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getQuestionCount()).isEqualTo(12);
    }
}
