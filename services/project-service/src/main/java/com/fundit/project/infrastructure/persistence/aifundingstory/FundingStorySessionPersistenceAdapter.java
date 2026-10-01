package com.fundit.project.infrastructure.persistence.aifundingstory;

import com.fundit.project.domain.aifundingstory.FundingStorySession;
import com.fundit.project.domain.aifundingstory.FundingStorySessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FundingStorySessionPersistenceAdapter implements FundingStorySessionRepository {

    private final FundingStorySessionJpaRepository jpaRepository;
    private final FundingStorySessionMapper mapper;

    @Override
    public FundingStorySession save(FundingStorySession session) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(session)));
    }

    @Override
    public Optional<FundingStorySession> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<FundingStorySession> findByProjectIdAndIdempotencyKey(Long projectId, String idempotencyKey) {
        return jpaRepository.findByProjectIdAndIdempotencyKey(projectId, idempotencyKey).map(mapper::toDomain);
    }

    @Override
    public boolean insertIfKeyFree(FundingStorySession session) {
        return jpaRepository.insertIfKeyFree(
                session.getId(),
                session.getProjectId(),
                session.getSellerId(),
                session.getProductDescription(),
                session.getStatus().name(),
                session.getIdempotencyKey()) > 0;
    }
}
