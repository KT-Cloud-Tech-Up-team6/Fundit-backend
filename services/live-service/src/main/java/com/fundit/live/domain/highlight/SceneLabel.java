package com.fundit.live.domain.highlight;

/**
 * 장면 유형 라벨(요구사항정의서 6.6.3). 타임라인에서는 구간 라벨, 쇼츠에서는 클립 분류로 쓴다.
 *
 * <p>DB에 CHECK 제약이 있는데 앱에는 검증이 없어, 모르는 값이 들어오면
 * <b>400이 아니라 500</b>이 났다. 값 검증을 DB까지 미루지 않는다(S2).
 */
public enum SceneLabel {
    DEMO,
    AUDIENCE_REACTION,
    SPEC,
    PRICE_BENEFIT,
    COMPARISON
}
