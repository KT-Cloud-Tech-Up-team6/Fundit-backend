package com.fundit.project.application.project;

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
import java.util.UUID;

import static org.mockito.Mockito.verify;

@DisplayName("ProjectReviewNotificationListener")
@ExtendWith(MockitoExtension.class)
class ProjectReviewNotificationListenerUnitTest {

    @Mock
    private NotificationPort notificationPort;

    @InjectMocks
    private ProjectReviewNotificationListener listener;

    @Nested
    class 오픈 {

        @Test
        void 커밋_후_오픈_알림을_보낸다() {
            // given
            UUID member = UUID.fromString("55555555-5555-4555-8555-555555555555");
            ProjectOpenedEvent event = new ProjectOpenedEvent(ProjectFixture.PROJECT_ID, List.of(member));

            // when
            listener.onProjectOpened(event);

            // then
            verify(notificationPort).notifyProjectOpened(ProjectFixture.PROJECT_ID, List.of(member));
        }
    }

    @Nested
    class 반려 {

        @Test
        void 커밋_후_반려_알림을_보낸다() {
            // given
            ProjectRejectedEvent event = new ProjectRejectedEvent(
                    ProjectFixture.PROJECT_ID, ProjectFixture.SELLER_ID, "서류 미비");

            // when
            listener.onProjectRejected(event);

            // then
            verify(notificationPort).notifyProjectRejected(
                    ProjectFixture.PROJECT_ID, ProjectFixture.SELLER_ID, "서류 미비");
        }
    }
}
