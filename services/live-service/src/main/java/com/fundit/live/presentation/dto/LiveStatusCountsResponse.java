package com.fundit.live.presentation.dto;

/**
 * 판매자 스튜디오 상태 탭 배지용(FE 요청). project의 {@code status-counts}와 달리
 * 임의로 그룹핑하지 않고 {@link com.fundit.live.domain.session.LiveStatus} 5종을 그대로 낸다 —
 * 그룹핑 기준(화면 탭 구성)이 아직 정해지지 않아 코드에서 먼저 묶으면 나중에 또 바뀐다.
 */
public record LiveStatusCountsResponse(long draft, long scheduled, long live, long ended, long error) {
}
