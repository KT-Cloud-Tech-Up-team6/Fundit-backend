package com.fundit.project.infrastructure.persistence.pagesummary;

import com.fundit.project.domain.pagesummary.PageSummarySection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** DB 컬럼 매핑 전용. 상태 전이는 {@code PageSummary} 도메인이 담당한다. */
@Getter
@Entity
@Builder
@Table(name = "project_page_summaries")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PageSummaryJpaEntity {

    @Id
    @Column(name = "project_id")
    private Long projectId;

    /** 쓰기 경로의 upsert만 쓴다 — 워커 저장이 동시에 올라간 값을 덮지 않게 읽기 전용으로 매핑한다. */
    @Column(name = "dirty_at", insertable = false, updatable = false)
    private Instant dirtyAt;

    @Column(name = "handled_dirty_at")
    private Instant handledDirtyAt;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "source_revision", nullable = false)
    private int sourceRevision;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "run_id")
    private UUID runId;

    @Column(length = 20)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<PageSummarySection> sections;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
