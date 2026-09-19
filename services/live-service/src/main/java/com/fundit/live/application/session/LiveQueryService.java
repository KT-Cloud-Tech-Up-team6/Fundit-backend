package com.fundit.live.application.session;

import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 목록 조회 전용. 읽기라 도메인 모델로 되돌리지 않고 JpaEntity를 그대로 읽는다 —
 * 상태 전이가 없는 경로에 Mapper를 한 번 더 태울 이유가 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LiveQueryService {

    private final LiveSessionJpaRepository sessionRepository;

    /** 판매자 본인 LIVE 목록(요구사항정의서 6.1.3). 임시저장(DRAFT)으로 돌아가는 유일한 경로다. */
    public Page<LiveSessionJpaEntity> findMine(UUID sellerId, LiveStatus status, Pageable pageable) {
        return sessionRepository.findMine(sellerId, status, pageable);
    }

    /** 소비자 목록. DRAFT 제외는 쿼리에 고정돼 있어 필터를 생략해도 새지 않는다. */
    public Page<LiveSessionJpaEntity> findPublic(LiveStatus status, Pageable pageable) {
        return sessionRepository.findPublic(status, pageable);
    }

    /** 홈 배너(요구사항정의서 10.1.4) — 현재 방송 중인 것만. */
    public List<LiveSessionJpaEntity> findLiveBanner() {
        return sessionRepository.findByStatusOrderByActualStartAtDesc(LiveStatus.LIVE);
    }
}
