package com.fundit.project.infrastructure.persistence.pagesummary;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PageSummaryJpaRepository extends JpaRepository<PageSummaryJpaEntity, Long> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into project_page_summaries (project_id, dirty_at)
            values (:projectId, :now)
            on conflict (project_id) do update set dirty_at = excluded.dirty_at
            """, nativeQuery = true)
    void markDirty(@Param("projectId") Long projectId, @Param("now") Instant now);

    @Query("""
            select s.projectId from PageSummaryJpaEntity s
             where s.handledDirtyAt is null
                or s.dirtyAt > s.handledDirtyAt
                or (s.status in ('REQUESTED', 'RETRY_WAIT') and s.nextAttemptAt <= :now)
             order by s.projectId
            """)
    List<Long> findDueProjectIds(@Param("now") Instant now, Pageable pageable);
}
