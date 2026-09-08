package com.fundit.project.domain.aifundingstory;

import java.util.Optional;
import java.util.UUID;

public interface FundingStorySessionRepository {

    FundingStorySession save(FundingStorySession session);

    Optional<FundingStorySession> findById(UUID id);
}
