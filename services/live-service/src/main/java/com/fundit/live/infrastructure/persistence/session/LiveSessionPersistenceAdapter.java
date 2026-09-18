package com.fundit.live.infrastructure.persistence.session;

import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class LiveSessionPersistenceAdapter implements LiveSessionRepository {

    private final LiveSessionJpaRepository jpaRepository;

    @Override
    public LiveSession save(LiveSession session) {
        return LiveSessionMapper.toDomain(jpaRepository.save(LiveSessionMapper.toEntity(session)));
    }

    @Override
    public Optional<LiveSession> findOwned(UUID publicId, UUID sellerId) {
        return jpaRepository.findOwned(publicId, sellerId).map(LiveSessionMapper::toDomain);
    }
}
