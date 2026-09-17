package com.fundit.search.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.search.application.projectdocument.ProjectIndexEventListener;
import com.fundit.search.application.projectdocument.ProjectIndexEventListener.ProjectIndexedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** SEARCH-011 — {@code project.approved.v1}/{@code project.updated.v1} 구독 어댑터. */
@Component
@RequiredArgsConstructor
public class ProjectIndexEventKafkaListener {

    private final ProjectIndexEventListener projectIndexEventListener;

    @KafkaListener(topics = KafkaTopics.PROJECT_APPROVED)
    public void onProjectApproved(ProjectIndexedEvent event) {
        projectIndexEventListener.onProjectApproved(event);
    }

    @KafkaListener(topics = KafkaTopics.PROJECT_UPDATED)
    public void onProjectUpdated(ProjectIndexedEvent event) {
        projectIndexEventListener.onProjectUpdated(event);
    }
}
