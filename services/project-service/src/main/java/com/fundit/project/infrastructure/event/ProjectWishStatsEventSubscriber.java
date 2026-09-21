package com.fundit.project.infrastructure.event;

import com.fundit.project.application.project.ProjectStatsService;
import com.fundit.project.application.project.ProjectUnwishedEvent;
import com.fundit.project.application.project.ProjectWishedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * ProjectWished/ProjectUnwished를 받아 찜 집계 읽기모델을 갱신한다.
 * {@link ProjectWishKafkaListener}가 member-service의 실제 Kafka 이벤트를 구독해 호출한다
 * (이전엔 Spring {@code @EventListener}로만 등록돼 있었는데, 같은 JVM 안에서 이 타입의
 * ApplicationEvent를 발행하는 코드가 없어 실제로는 아무도 호출하지 않는 죽은 코드였다).
 */
@Component
@RequiredArgsConstructor
public class ProjectWishStatsEventSubscriber {

    private final ProjectStatsService projectStatsService;

    public void onProjectWished(ProjectWishedEvent event) {
        projectStatsService.applyProjectWished(event.projectId(), event.memberId());
    }

    public void onProjectUnwished(ProjectUnwishedEvent event) {
        projectStatsService.applyProjectUnwished(event.projectId(), event.memberId());
    }
}
