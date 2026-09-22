package com.fundit.live.application.like;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.infrastructure.persistence.like.LiveLikeJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * LIVE 좋아요(요구사항정의서 11.2.4). PUT·DELETE 둘 다 idempotent다 —
 * 네트워크 재시도로 같은 요청이 두 번 와도 카운트가 두 번 오르면 안 된다.
 */
@Service
@RequiredArgsConstructor
public class LiveLikeService {

    private final LiveLikeJpaRepository likeRepository;
    private final LiveSessionJpaRepository sessionRepository;

    @Transactional
    public void like(UUID memberId, UUID liveId) {
        Long sessionId = sessionId(liveId);
        // 실제로 새로 들어갔을 때만 카운트를 올린다. 삽입이 중복이면 0이라 그대로 넘어간다.
        if (likeRepository.insertIgnoringConflict(sessionId, memberId) > 0) {
            sessionRepository.addLikeCount(sessionId, 1);
        }
    }

    @Transactional
    public void unlike(UUID memberId, UUID liveId) {
        Long sessionId = sessionId(liveId);
        if (likeRepository.deleteByIds(sessionId, memberId) > 0) {
            sessionRepository.addLikeCount(sessionId, -1);
        }
    }

    /** 소비자 경로라 DRAFT는 404다 — 설정 중인 방송에 좋아요가 적립되면 안 된다. */
    private Long sessionId(UUID liveId) {
        return sessionRepository.findPublicByPublicId(liveId)
                .map(LiveSessionJpaEntity::getId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
