package com.fundit.project.infrastructure.persistence.notice;

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
 * 단순 애그리거트(persistence-convention.md §2). title/content는 PATCH(재편집)로 수정 가능 —
 * updated_at은 trg_project_notices_updated_at DB 트리거가 갱신한다(reward_option_groups와 동일 패턴).
 */
@Getter
@Entity
@Builder
@Table(name = "project_notices")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectNoticeJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "notice_type", nullable = false, length = 20)
    private String noticeType;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.updatedAt == null) this.updatedAt = now;
    }
}
