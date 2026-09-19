package com.fundit.live.domain.ai;

/**
 * AI 비동기 생성 상태. 큐시트와 하이라이트가 같은 값을 쓴다 — 둘 다 "요청 → AI가 결과를 밀어줌"
 * 이라는 한 가지 흐름이고, 따로 두면 같은 세 값이 두 벌이 된다.
 *
 * <pre>
 * GENERATING ──▶ COMPLETED
 *            └──▶ FAILED
 * </pre>
 *
 * 전이는 {@code LiveCueSheet}·{@code LiveHighlight}가 강제한다.
 */
public enum GenerationStatus {
    GENERATING,
    COMPLETED,
    FAILED
}
