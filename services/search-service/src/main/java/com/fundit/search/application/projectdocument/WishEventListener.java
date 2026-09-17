package com.fundit.search.application.projectdocument;

import java.util.UUID;

/**
 * SEARCH-014 인바운드 포트 — member-service가 이미 발행 중인 기존 이벤트를 재사용한다
 * (project-service {@code ProjectWishedEvent}/{@code ProjectUnwishedEvent}와 동일 계약).
 */
public interface WishEventListener {

    void onProjectWished(ProjectWishedEvent event);

    void onProjectUnwished(ProjectUnwishedEvent event);

    record ProjectWishedEvent(Long projectId, UUID memberId) {
    }

    record ProjectUnwishedEvent(Long projectId, UUID memberId) {
    }
}
