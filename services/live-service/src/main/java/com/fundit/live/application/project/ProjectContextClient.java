package com.fundit.live.application.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * AI {@code prepare}/{@code updateContext} 배선에 필요한 프로젝트 정보 조회.
 * {@link ProjectOwnershipClient}는 소유권 검증용 최소 필드(sellerId)만 보므로 분리한다.
 */
public interface ProjectContextClient {

    Optional<ProjectContext> find(UUID projectId);

    record ProjectContext(String title, String categoryMajor, String categoryMinor,
                          List<String> introTexts, Integer achievementRate, Integer remainingDays) {
    }
}
