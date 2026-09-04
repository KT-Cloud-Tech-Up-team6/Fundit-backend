package com.fundit.project.application.project;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.project.application.port.NotificationPort;
import com.fundit.project.application.project.event.ProjectOpenedEvent;
import com.fundit.project.application.project.event.ProjectRejectedEvent;
import com.fundit.project.fixture.ProjectFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

@DisplayName("ProjectReviewNotificationListener 예외")
@ExtendWith(MockitoExtension.class)
class ProjectReviewNotificationListenerUnitExceptionTest {

    @Mock
    private NotificationPort notificationPort;

    @InjectMocks
    private ProjectReviewNotificationListener listener;

    @Nested
    class 오픈 {

        @Test
        void 알림_실패해도_예외를_다시_던지지_않는다() {
            // given
            doThrow(new DependencyFailureException(new RuntimeException("알림 장애")))
                    .when(notificationPort).notifyProjectOpened(any(), anyList());

            // when & then
            listener.onProjectOpened(new ProjectOpenedEvent(ProjectFixture.PROJECT_ID, List.of()));
        }
    }

    @Nested
    class 반려 {

        @Test
        void 알림_실패해도_예외를_다시_던지지_않는다() {
            // given
            doThrow(new DependencyFailureException(new RuntimeException("알림 장애")))
                    .when(notificationPort).notifyProjectRejected(any(), any(), anyString());

            // when & then
            listener.onProjectRejected(
                    new ProjectRejectedEvent(ProjectFixture.PROJECT_ID, ProjectFixture.SELLER_ID, "서류 미비"));
        }
    }
}
