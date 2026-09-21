package com.fundit.project.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.project.application.project.ProjectUnwishedEvent;
import com.fundit.project.application.project.ProjectWishedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * PROJECT-016 — member-service가 발행하는 찜 이벤트를 구독해 {@link ProjectWishStatsEventSubscriber}로
 * 위임하는 얇은 어댑터. 비즈니스 로직은 여기 두지 않는다(order-service {@code RewardEventKafkaListener}와 동일 패턴).
 */
@Component
@RequiredArgsConstructor
public class ProjectWishKafkaListener {

    private final ProjectWishStatsEventSubscriber subscriber;

    @KafkaListener(topics = KafkaTopics.PROJECT_WISHED, groupId = "project-service")
    public void onProjectWished(ProjectWishedEvent event) {
        subscriber.onProjectWished(event);
    }

    @KafkaListener(topics = KafkaTopics.PROJECT_UNWISHED, groupId = "project-service")
    public void onProjectUnwished(ProjectUnwishedEvent event) {
        subscriber.onProjectUnwished(event);
    }
}
