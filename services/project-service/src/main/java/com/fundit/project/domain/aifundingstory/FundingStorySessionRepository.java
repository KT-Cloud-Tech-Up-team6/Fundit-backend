package com.fundit.project.domain.aifundingstory;

import java.util.Optional;
import java.util.UUID;

public interface FundingStorySessionRepository {

    FundingStorySession save(FundingStorySession session);

    Optional<FundingStorySession> findById(UUID id);

    /** run ID를 받기 전 폐기 요청(QA-189)을 매칭하기 위한 조회. */
    Optional<FundingStorySession> findByProjectIdAndIdempotencyKey(Long projectId, String idempotencyKey);

    /**
     * idempotency key가 비어 있을 때만 저장한다(원자적 — 같은 키 경합을 DB가 판정한다). 결과·추가질문이
     * 없는 새 행(run 추적자/폐기 선점 행)에만 쓴다. 키를 이미 누가 점유했으면 {@code false}를 돌려주고
     * 아무것도 쓰지 않는다 — 호출자는 그 행을 다시 읽어 처리한다.
     */
    boolean insertIfKeyFree(FundingStorySession session);
}
