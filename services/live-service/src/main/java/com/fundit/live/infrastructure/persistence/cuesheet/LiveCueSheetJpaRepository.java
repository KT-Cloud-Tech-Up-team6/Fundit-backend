package com.fundit.live.infrastructure.persistence.cuesheet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface LiveCueSheetJpaRepository extends JpaRepository<LiveCueSheetJpaEntity, Long> {

    /**
     * 커밋 후 이벤트 발행~AI 호출 사이 서버가 죽으면(인메모리 이벤트라 유실) 이 행이
     * GENERATING에 영영 남고, requestGeneration()의 중복요청 가드 때문에 판매자가 재시도조차
     * 못 하게 막힌다 — {@link com.fundit.live.infrastructure.cuesheet.CueSheetGenerationRecoveryWorker}가
     * 주기적으로 오래된 GENERATING을 FAILED로 풀어 재시도 버튼을 다시 누를 수 있게 한다.
     */
    @Modifying
    @Query("UPDATE LiveCueSheetJpaEntity c SET c.status = com.fundit.live.domain.ai.GenerationStatus.FAILED, "
            + "c.failureReason = :reason WHERE c.status = com.fundit.live.domain.ai.GenerationStatus.GENERATING "
            + "AND c.updatedAt < :before")
    int failStaleGenerating(@Param("before") Instant before, @Param("reason") String reason);
}
