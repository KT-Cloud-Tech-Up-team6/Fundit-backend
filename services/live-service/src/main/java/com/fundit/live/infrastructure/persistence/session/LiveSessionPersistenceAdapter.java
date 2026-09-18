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

    /**
     * 신규는 insert, 기존은 <b>관리 엔티티를 불러와 변경</b>한다.
     *
     * <p>detached 엔티티를 merge하면 전 컬럼 UPDATE가 되어, 도메인이 쓰지도 않는
     * {@code likeCount}·{@code vodUrl}까지 읽은 시점의 값으로 되돌린다.
     * 조건부 UPDATE로 지켜둔 좋아요 카운트가 여기서 무력화된다.
     */
    @Override
    public LiveSession save(LiveSession session) {
        if (session.getId() == null) {
            return LiveSessionMapper.toDomain(jpaRepository.save(LiveSessionMapper.toEntity(session)));
        }
        LiveSessionJpaEntity managed = jpaRepository.findById(session.getId())
                .orElseThrow(() -> new IllegalStateException("없는 세션을 저장하려 했다: " + session.getId()));
        managed.applyFrom(session);
        return LiveSessionMapper.toDomain(managed);
    }

    @Override
    public Optional<LiveSession> findOwned(UUID publicId, UUID sellerId) {
        return jpaRepository.findOwned(publicId, sellerId).map(LiveSessionMapper::toDomain);
    }

    @Override
    public Optional<LiveSession> findOwnedAny(UUID publicId) {
        return jpaRepository.findByPublicId(publicId).map(LiveSessionMapper::toDomain);
    }
}
