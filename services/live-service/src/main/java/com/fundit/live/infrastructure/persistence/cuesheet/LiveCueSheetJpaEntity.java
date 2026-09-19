package com.fundit.live.infrastructure.persistence.cuesheet;

import com.fundit.live.domain.ai.GenerationStatus;
import com.fundit.live.domain.cuesheet.LiveCueSheet;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * <p>저장 매핑만 한다. {@code GENERATING → COMPLETED/FAILED} 전이와 "생성 중에는 수정 불가"는
 * {@link LiveCueSheet}에 있다(persistence-convention.md 1번).
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

    @Id
    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "mode", nullable = false)
    private String mode;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private GenerationStatus status;

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

    /** 관리 엔티티에 도메인 변경분을 옮긴다. 재생성은 mode·길이도 바뀔 수 있어 같이 덮는다. */
    void applyFrom(LiveCueSheet cueSheet) {
        this.mode = cueSheet.getMode();
        this.targetDurationSec = cueSheet.getTargetDurationSec();
        this.status = cueSheet.getStatus();
        this.segments = cueSheet.getSegments();
        this.failureReason = cueSheet.getFailureReason();
    }
}
