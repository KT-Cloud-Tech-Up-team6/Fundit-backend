package com.fundit.live.domain.session;

/**
 * 방송 상태. 전이는 {@link LiveSession}이 강제한다.
 *
 * <pre>
 * DRAFT ──(예정일시 입력)──▶ SCHEDULED ──┐
 *   └────────(즉시 시작)──────────────┴──▶ LIVE ──▶ ENDED
 *                                          └──▶ ERROR (송출 실패)
 * </pre>
 */
public enum LiveStatus {
    /** 생성 직후. 설정이 끝나지 않았으므로 소비자 목록에 절대 노출되지 않는다. */
    DRAFT,
    /** 방송 예정일시가 채워진 상태. */
    SCHEDULED,
    /** 송출 중. */
    LIVE,
    /** 종료. VOD 전환 대기 포함. */
    ENDED,
    /** 송출 실패. error_detail에 사유가 남는다. */
    ERROR
}
