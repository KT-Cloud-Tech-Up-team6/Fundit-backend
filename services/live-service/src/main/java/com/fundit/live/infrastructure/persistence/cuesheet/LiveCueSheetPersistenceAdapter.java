package com.fundit.live.infrastructure.persistence.cuesheet;

import com.fundit.live.domain.cuesheet.LiveCueSheet;
import com.fundit.live.domain.cuesheet.LiveCueSheetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class LiveCueSheetPersistenceAdapter implements LiveCueSheetRepository {

    private final LiveCueSheetJpaRepository jpaRepository;

    /**
     * 세션당 1행이고 PK가 sessionId라 <b>재생성은 기존 행을 덮어쓴다</b>.
     * 있으면 관리 엔티티를 변경하고, 없으면 insert한다.
     */
    @Override
    public LiveCueSheet save(LiveCueSheet cueSheet) {
        return jpaRepository.findById(cueSheet.getSessionId())
                .map(managed -> {
                    managed.applyFrom(cueSheet);
                    return LiveCueSheetMapper.toDomain(managed);
                })
                .orElseGet(() -> LiveCueSheetMapper.toDomain(
                        jpaRepository.save(LiveCueSheetMapper.toEntity(cueSheet))));
    }

    @Override
    public Optional<LiveCueSheet> findBySessionId(Long sessionId) {
        return jpaRepository.findById(sessionId).map(LiveCueSheetMapper::toDomain);
    }
}
