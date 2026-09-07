package com.fundit.project.infrastructure.event;

import com.fundit.project.application.project.ProjectStatsService;
import com.fundit.project.application.project.ProjectUnwishedEvent;
import com.fundit.project.application.project.ProjectWishedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * ProjectWished/ProjectUnwished를 받아 찜 집계 읽기모델을 갱신한다.
 * 브로커 어댑터가 이 애플리케이션 이벤트로 변환해 발행하면 된다.
 */
@Component
@RequiredArgsConstructor
public class ProjectWishStatsEventSubscriber {

    private final ProjectStatsService projectStatsService;

    @EventListener
    public void onProjectWished(ProjectWishedEvent event) {
        projectStatsService.applyProjectWished(event.projectId(), event.memberId());
    }

    @EventListener
    public void onProjectUnwished(ProjectUnwishedEvent event) {
        projectStatsService.applyProjectUnwished(event.projectId(), event.memberId());
    }
}
