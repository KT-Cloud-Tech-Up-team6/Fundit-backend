package com.fundit.live.application.like;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.infrastructure.persistence.like.LiveLikeId;
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

    /**
     * 갱신된 likeCount를 돌려준다 — {@code addLikeCount}(네이티브 벌크 UPDATE) 직후
     * {@code findLikeCount}로 다시 읽는다. 갱신 전 값에 델타만 더해 계산하면 동시 요청 사이에
     * 서로의 반영분을 못 보고 <b>DB는 맞는데 응답값만 틀린다</b>(리뷰 지적으로 발견) — A·B가
     * 동시에 누르면 둘 다 상대방 반영 전 숫자를 돌려받는다.
     */
    @Transactional
    public int like(UUID memberId, UUID liveId) {
        LiveSessionJpaEntity session = loadPublic(liveId);
        if (likeRepository.insertIgnoringConflict(session.getId(), memberId) > 0) {
            sessionRepository.addLikeCount(session.getId(), 1);
        }
        return sessionRepository.findLikeCount(session.getId());
    }

    @Transactional
    public int unlike(UUID memberId, UUID liveId) {
        LiveSessionJpaEntity session = loadPublic(liveId);
        if (likeRepository.deleteByIds(session.getId(), memberId) > 0) {
            sessionRepository.addLikeCount(session.getId(), -1);
        }
        return sessionRepository.findLikeCount(session.getId());
    }

    /** 내가 이 LIVE에 좋아요를 눌렀는지. 로그인 사용자 전용이라 인증이 필수다. */
    @Transactional(readOnly = true)
    public boolean isLiked(UUID memberId, UUID liveId) {
        LiveSessionJpaEntity session = loadPublic(liveId);
        return likeRepository.existsById(new LiveLikeId(session.getId(), memberId));
    }

    /** 소비자 경로라 DRAFT는 404다 — 설정 중인 방송에 좋아요가 적립되면 안 된다. */
    private LiveSessionJpaEntity loadPublic(UUID liveId) {
        return sessionRepository.findPublicByPublicId(liveId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
