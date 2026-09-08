package com.fundit.project.infrastructure.event;

import com.fundit.project.application.project.ProjectStatsService;
import com.fundit.project.application.project.ProjectUnwishedEvent;
import com.fundit.project.application.project.ProjectWishedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProjectWishStatsEventSubscriberUnitTest {

    @Mock
    private ProjectStatsService projectStatsService;

    @InjectMocks
    private ProjectWishStatsEventSubscriber subscriber;

    @Test
    void ProjectWished를_받으면_집계를_반영한다() {
        // given
        UUID memberId = UUID.randomUUID();

        // when
        subscriber.onProjectWished(new ProjectWishedEvent(1L, memberId));

        // then
        verify(projectStatsService).applyProjectWished(1L, memberId);
    }

    @Test
    void ProjectUnwished를_받으면_집계를_반영한다() {
        // given
        UUID memberId = UUID.randomUUID();

        // when
        subscriber.onProjectUnwished(new ProjectUnwishedEvent(1L, memberId));

        // then
        verify(projectStatsService).applyProjectUnwished(1L, memberId);
    }
}
