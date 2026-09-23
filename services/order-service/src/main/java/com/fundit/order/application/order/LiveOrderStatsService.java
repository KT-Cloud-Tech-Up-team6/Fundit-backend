package com.fundit.order.application.order;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.order.application.live.LiveStatusClient;
import com.fundit.order.infrastructure.persistence.funding.FundingJpaRepository;
import com.fundit.order.infrastructure.persistence.funding.query.LiveOrderStatsProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 방송 중 화면(FL_S_LV_STREAM)의 주문 건수·매출 지표. 순수 조회라 persistence-convention.md §3에
 * 따라 프로젝션 JpaRepository를 직접 쓴다({@link com.fundit.order.application.supporter.SupporterActivityService}와 동일).
 *
 * <p>{@code @Transactional}을 붙이지 않은 이유: live-service 호출이 먼저 있고 그 뒤 단건 조회
 * 하나뿐이다 — 트랜잭션을 열면 외부 응답을 기다리는 동안 DB 커넥션을 쥐고 있게 된다.
 */
@Service
@RequiredArgsConstructor
public class LiveOrderStatsService {

    private final LiveStatusClient liveStatusClient;
    private final FundingJpaRepository fundingJpaRepository;

    /**
     * 소유권은 live가 주는 {@code sellerId}로만 판정한다(확정 계약 2번) — project-service를 거치지
     * 않는다. 세션이 없으면 404, live 조회 실패면 클라이언트가 던지는 예외가 그대로 전파돼 503이다.
     */
    public LiveOrderStatsProjection getStats(UUID sellerId, UUID liveId) {
        LiveStatusClient.LiveStatus live = liveStatusClient.findByLiveId(liveId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "존재하지 않는 방송입니다."));
        if (!sellerId.equals(live.sellerId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return fundingJpaRepository.findLiveOrderStats(live.sessionId());
    }
}
