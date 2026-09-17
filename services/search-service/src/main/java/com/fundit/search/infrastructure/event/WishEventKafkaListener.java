package com.fundit.search.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.search.application.projectdocument.WishEventListener;
import com.fundit.search.application.projectdocument.WishEventListener.ProjectUnwishedEvent;
import com.fundit.search.application.projectdocument.WishEventListener.ProjectWishedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** SEARCH-014 — {@code project.wished.v1}/{@code project.unwished.v1} 구독 어댑터. */
@Component
@RequiredArgsConstructor
public class WishEventKafkaListener {

    private final WishEventListener wishEventListener;

    @KafkaListener(topics = KafkaTopics.PROJECT_WISHED)
    public void onProjectWished(ProjectWishedEvent event) {
        wishEventListener.onProjectWished(event);
    }

    @KafkaListener(topics = KafkaTopics.PROJECT_UNWISHED)
    public void onProjectUnwished(ProjectUnwishedEvent event) {
        wishEventListener.onProjectUnwished(event);
    }
}
