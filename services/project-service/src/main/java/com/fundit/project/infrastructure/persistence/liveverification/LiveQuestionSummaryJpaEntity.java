package com.fundit.project.infrastructure.persistence.liveverification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * live-service가 발행한 {@code live.questions-summarized.v1}의 로컬 복제본 — 대표 질문 문구와 발생 건수만 갖는다.
 * 판매자 답변은 {@link LiveVerificationJpaEntity}가 갖고, 여기엔 두지 않는다(소유 분리).
 * updated_at은 DB 트리거가 관리한다(LiveVerificationJpaEntity와 동일한 이유).
 */
@Getter
@Entity
@Builder
@Table(name = "live_question_summaries")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveQuestionSummaryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "question_summary_id", nullable = false, length = 100)
    private String questionSummaryId;

    @Column(name = "summary_text", nullable = false, columnDefinition = "TEXT")
    private String summaryText;

    @Column(name = "question_count", nullable = false)
    private Integer questionCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.updatedAt == null) this.updatedAt = now;
        if (this.questionCount == null) this.questionCount = 0;
    }

    /** 같은 이벤트가 다시 오거나 요약이 갱신 발행됐을 때 문구/건수만 덮어쓴다. */
    public void applySummary(String summaryText, int questionCount) {
        this.summaryText = summaryText;
        this.questionCount = questionCount;
    }
}
