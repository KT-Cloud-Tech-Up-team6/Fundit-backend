package com.fundit.order.application.live;

import java.util.Optional;
import java.util.UUID;

/**
 * live-service 내부 API(`/internal/v1/lives/**`) 조회 포트.
 * 호출 방향은 order → live다(라이브 꼬리표·쿠폰 게이트의 주인이 order이므로 판정 정보를 이쪽이 가져간다).
 *
 * <p><b>"세션 없음"과 "호출 실패"는 반드시 다른 타입으로 돌려준다.</b> 세션 없음은
 * {@code Optional.empty()}, 타임아웃·5xx·본문 없음은 {@link com.fundit.common.error.DependencyFailureException}이다.
 * 둘을 같은 값으로 뭉개면 "쿠폰은 못 믿으면 막고(게이트), 주문의 라이브ID는 못 달아도 진행(꼬리표)"이라는
 * 정책을 호출부에서 구분할 수 없게 된다.
 */
public interface LiveStatusClient {

    /** 공개 집계 API용 — liveId(UUID)로 sessionId·sellerId를 얻는다. */
    Optional<LiveStatus> findByLiveId(UUID liveId);

    /** 주문 생성 시점엔 order가 projectId만 알아서 필요한 경로. LIVE 세션이 없으면 empty. */
    Optional<LiveStatus> findActiveByProject(UUID projectId);

    /** 쿠폰 검증용 — order DB에 저장된 건 BIGINT(coupons.live_session_id)뿐이다. */
    Optional<LiveStatus> findBySessionId(Long sessionId);

    record LiveStatus(UUID liveId, Long sessionId, String status, UUID sellerId) {
    }
}
