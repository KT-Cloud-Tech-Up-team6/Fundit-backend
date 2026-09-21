package com.fundit.project.application.project;

import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundingDeadlineWatcherUnitTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private FundingDeadlinePublisher fundingDeadlinePublisher;

    private FundingDeadlineWatcher watcher;

    private Project ongoingProject(Long id, long goalAmount) {
        return Project.builder()
                .id(id)
                .publicId(UUID.randomUUID())
                .sellerId(UUID.randomUUID())
                .status(ProjectStatus.ONGOING)
                .goalAmount(goalAmount)
                .fundingDeadline(Instant.now().minusSeconds(60))
                .build();
    }

    @Test
    void 마감_도래한_프로젝트를_발행하고_통지_표시를_남긴다() {
        // given
        watcher = new FundingDeadlineWatcher(projectRepository, fundingDeadlinePublisher, 200);
        Project project = ongoingProject(1L, 5_000_000L);
        when(projectRepository.findOngoingWithDeadlineReached(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(project));

        // when
        watcher.detectReachedDeadlines();

        // then
        ArgumentCaptor<FundingDeadlinePublisher.FundingDeadlineReachedEvent> captor =
                ArgumentCaptor.forClass(FundingDeadlinePublisher.FundingDeadlineReachedEvent.class);
        verify(fundingDeadlinePublisher).publishFundingDeadlineReached(captor.capture());
        assertThat(captor.getValue().projectId()).isEqualTo(1L);
        assertThat(captor.getValue().goalAmount()).isEqualTo(5_000_000L);

        assertThat(project.getDeadlineNotifiedAt()).isNotNull();
        verify(projectRepository).save(project);
    }

    @Test
    void 배치_크기만큼만_조회한다() {
        // given
        watcher = new FundingDeadlineWatcher(projectRepository, fundingDeadlinePublisher, 3);
        when(projectRepository.findOngoingWithDeadlineReached(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());

        // when
        watcher.detectReachedDeadlines();

        // then — 마감이 한 번에 몰려도 한 트랜잭션에서 배치 크기 이상은 읽지 않는다
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(projectRepository).findOngoingWithDeadlineReached(any(Instant.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(3);
    }

    @Test
    void 대상이_없으면_아무것도_발행하지_않는다() {
        // given
        watcher = new FundingDeadlineWatcher(projectRepository, fundingDeadlinePublisher, 200);
        when(projectRepository.findOngoingWithDeadlineReached(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());

        // when
        watcher.detectReachedDeadlines();

        // then
        verify(fundingDeadlinePublisher, never()).publishFundingDeadlineReached(any());
        verify(projectRepository, never()).save(any());
    }
}
