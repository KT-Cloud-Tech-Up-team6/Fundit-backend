package com.fundit.live.infrastructure.persistence.highlight;

import com.fundit.live.domain.highlight.HighlightKind;
import com.fundit.live.domain.highlight.LiveHighlight;
import com.fundit.live.domain.highlight.LiveHighlightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class LiveHighlightPersistenceAdapter implements LiveHighlightRepository {

    private final LiveHighlightJpaRepository jpaRepository;

    /**
     * 신규는 insert, 기존은 <b>관리 엔티티를 불러와 변경</b>한다(LiveSession과 같은 이유).
     * detached 엔티티를 merge하면 전 컬럼 UPDATE가 되어 조회·클릭 수까지 되돌린다.
     */
    @Override
    public LiveHighlight save(LiveHighlight highlight) {
        if (highlight.getId() == null) {
            return LiveHighlightMapper.toDomain(
                    jpaRepository.save(LiveHighlightMapper.toEntity(highlight)));
        }
        LiveHighlightJpaEntity managed = jpaRepository.findById(highlight.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "없는 하이라이트를 저장하려 했다: " + highlight.getId()));
        managed.applyFrom(highlight);
        return LiveHighlightMapper.toDomain(managed);
    }

    @Override
    public Optional<LiveHighlight> findByPublicId(UUID publicId) {
        return jpaRepository.findByPublicId(publicId).map(LiveHighlightMapper::toDomain);
    }

    @Override
    public List<LiveHighlight> findAllBySessionId(Long sessionId) {
        return jpaRepository.findBySessionIdOrderByStartSecAsc(sessionId).stream()
                .map(LiveHighlightMapper::toDomain).toList();
    }

    @Override
    public List<LiveHighlight> findPublicBySessionId(Long sessionId) {
        return jpaRepository.findBySessionIdAndIsPublicTrueOrderByStartSecAsc(sessionId).stream()
                .map(LiveHighlightMapper::toDomain).toList();
    }

    @Override
    public long countClips(Long sessionId) {
        return jpaRepository.countBySessionIdAndKind(sessionId, HighlightKind.CLIP);
    }

    @Override
    public void deleteByPublicId(UUID publicId) {
        jpaRepository.findByPublicId(publicId).ifPresent(jpaRepository::delete);
    }

    @Override
    public void increaseViewCount(Long sessionId) {
        jpaRepository.increaseViewCount(sessionId);
    }

    @Override
    public void increaseClickCount(UUID publicId) {
        jpaRepository.increaseClickCount(publicId);
    }
}
