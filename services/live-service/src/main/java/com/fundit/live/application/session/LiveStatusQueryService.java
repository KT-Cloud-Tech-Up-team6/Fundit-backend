package com.fundit.live.application.session;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 내부 전용 방송 상태 조회 — order-service의 라이브 쿠폰 검증용.
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
        LiveSessionJpaEntity session = sessionRepository.findByPublicId(liveId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        UUID sellerId = channelRepository.findById(session.getChannelId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND))
                .getSellerId();
        return new LiveStatus(session.getPublicId(), session.getId(), session.getStatus().name(), sellerId);
    }

    public record LiveStatus(UUID liveId, Long sessionId, String status, UUID sellerId) {
    }
}
