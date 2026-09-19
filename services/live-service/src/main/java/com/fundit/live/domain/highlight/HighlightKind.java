package com.fundit.live.domain.highlight;

/** 타임라인 마커와 쇼츠 클립. 컬럼이 거의 같아 한 테이블에 담고 이 값으로 구분한다. */
public enum HighlightKind {
    /** 시점만 있는 타임라인 마커. 개수 제한이 없고 {@code endSec}이 null이다. */
    MARKER,
    /** 구간이 있는 쇼츠 클립. 방송 1회당 3개까지다(요구사항정의서 6.6.3). */
    CLIP
}
