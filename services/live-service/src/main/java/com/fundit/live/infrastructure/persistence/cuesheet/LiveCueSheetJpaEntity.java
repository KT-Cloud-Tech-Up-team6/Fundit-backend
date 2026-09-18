package com.fundit.live.infrastructure.persistence.cuesheet;

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

/**
 * AI 큐시트. <b>세션당 1행</b>이라 session_id가 곧 PK다.
 *
 * <p>단순 애그리거트로 둔다 — {@code GENERATING} 중복 차단은 상태 전이가 아니라
 * 단일 조건 검사다. 복잡으로 잡으면 쓰이지 않는 파일 3개가 생긴다.
 *
 * <p>구간 배열이 JSONB인 이유: 구간은 항상 큐시트 전체와 함께 읽고 쓰며 구간 단위로
 * 조회·조인할 일이 없다. 테이블로 쪼개면 order 재정렬마다 행 재배치가 생기고 얻는 게 없다.
 */
@Getter
@Entity
@Builder
@Table(name = "live_cue_sheets")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveCueSheetJpaEntity {

    public static final String STATUS_GENERATING = "GENERATING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "mode", nullable = false)
    private String mode;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "target_duration_sec", nullable = false)
    private int targetDurationSec;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "segments")
    private String segments;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    public void complete(String segments) {
        this.status = STATUS_COMPLETED;
        this.segments = segments;
        this.failureReason = null;
    }

    public void fail(String reason) {
        this.status = STATUS_FAILED;
        this.failureReason = reason;
    }

    /** 판매자 직접 수정(요구사항정의서 6.2.4.2) — 구간 추가·순서 변경도 이 경로다. */
    public void replaceSegments(String segments) {
        this.segments = segments;
    }

    public boolean isGenerating() {
        return STATUS_GENERATING.equals(this.status);
    }
}
