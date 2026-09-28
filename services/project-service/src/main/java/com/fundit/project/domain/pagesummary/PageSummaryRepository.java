package com.fundit.project.domain.pagesummary;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PageSummaryRepository {

    Optional<PageSummary> findByProjectId(Long projectId);

    PageSummary save(PageSummary summary);

    /** 행이 없으면 만들고 {@code dirty_at}만 올린다(원자적 upsert) — 워커가 쓰는 컬럼은 건드리지 않는다. */
    void markDirty(Long projectId, Instant now);

    /** 입력이 바뀌었거나 폴링·재시도 시각이 된 프로젝트. */
    List<Long> findDueProjectIds(Instant now, int limit);
}
