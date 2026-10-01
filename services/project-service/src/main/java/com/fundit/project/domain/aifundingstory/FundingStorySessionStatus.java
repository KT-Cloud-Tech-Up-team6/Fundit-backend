package com.fundit.project.domain.aifundingstory;

public enum FundingStorySessionStatus {
    GENERATING,
    COMPLETED,
    FAILED,
    /** 생성 중 판매자가 창을 닫고 결과 폐기를 선택한 run(QA-189) — callback이 와도 스토리에 반영하지 않는다. */
    DISCARDED
}
