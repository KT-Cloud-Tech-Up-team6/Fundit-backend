package com.fundit.live.application.session;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 내부 전용 방송 상태 조회 — order-service의 라이브 쿠폰 검증·주문 집계용.
 * 호출 방향은 order → live다(쿠폰의 주인이 order이므로 판정 정보를 그쪽이 가져간다).
 *
 * <p>컨트롤러가 리포지토리를 직접 쓰지 않게 여기로 뺐다(api-convention.md:
 * 컨트롤러는 요청 검증 + 서비스 호출 + 응답 변환만).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LiveStatusQueryService {

    private final LiveSessionJpaRepository sessionRepository;
    private final LiveChannelJpaRepository channelRepository;

    public LiveStatus find(UUID liveId) {
        return toStatus(sessionRepository.findByPublicId(liveId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND)));
    }

    /** 주문 생성 시 세션 꼬리표용. 지금 방송 중인 세션이 없으면 empty — 흔한 정상 상태다. */
    public Optional<LiveStatus> findActiveByProject(UUID projectId) {
        return sessionRepository.findFirstByProjectIdAndStatus(projectId,
                        com.fundit.live.domain.session.LiveStatus.LIVE)
                .map(this::toStatus);
    }

    /**
     * 쿠폰 검증용 — order가 가진 건 내부 세션 PK뿐이다. 세션이 있으면 LIVE가 아니어도 실제 상태를
     * 그대로 돌려준다: order는 "없음(empty)"일 때만 정합성 경고를 남기므로 둘을 섞으면 안 된다.
     */
    public Optional<LiveStatus> findBySessionId(Long sessionId) {
        return sessionRepository.findById(sessionId).map(this::toStatus);
    }

    private LiveStatus toStatus(LiveSessionJpaEntity session) {
        UUID sellerId = channelRepository.findById(session.getChannelId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND))
                .getSellerId();
        return new LiveStatus(session.getPublicId(), session.getId(), session.getStatus().name(), sellerId);
    }

    public record LiveStatus(UUID liveId, Long sessionId, String status, UUID sellerId) {
    }
}
