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
 * 단순 애그리거트 — updated_at은 DB 트리거(trg_live_verifications_updated_at)가 관리하므로
 * @PreUpdate를 두지 않는다(ProjectJpaEntity와 동일한 이유).
 */
@Getter
@Entity
@Builder
@Table(name = "live_verifications")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveVerificationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "question_summary_id", nullable = false, length = 100)
    private String questionSummaryId;

    @Column(name = "question_count", nullable = false)
    private Integer questionCount;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.updatedAt == null) this.updatedAt = now;
        if (this.questionCount == null) this.questionCount = 0;
    }

    public void changeAnswer(String answer) {
        this.answer = answer;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }
}
