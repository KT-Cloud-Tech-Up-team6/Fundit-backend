package com.fundit.project.domain.aifundingstory;

import java.util.Optional;
import java.util.UUID;

public interface FundingStorySessionRepository {

    FundingStorySession save(FundingStorySession session);

    Optional<FundingStorySession> findById(UUID id);

    /** run ID를 받기 전 폐기 요청(QA-189)을 매칭하기 위한 조회. */
    Optional<FundingStorySession> findByProjectIdAndIdempotencyKey(Long projectId, String idempotencyKey);
}
