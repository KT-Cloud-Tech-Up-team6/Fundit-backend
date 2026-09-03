package com.fundit.member.infrastructure.persistence.wish;

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
import java.util.UUID;

/**
 * 단순 애그리거트(persistence-convention.md §2). project_title/project_thumbnail_url/
 * snapshot_synced_at는 catalog-service 이벤트 구독으로 채워지는 스냅샷 — 이번 MVP는
 * 이벤트 스키마가 미확정이라 구독 로직 자체는 구현하지 않고 컬럼만 nullable로 남겨둔다
 * (등록 시점엔 항상 null, 값이 채워지기 전까지는 찜 목록에서 프로젝트 정보가 비어 보인다).
 */
@Getter
@Entity
@Builder
@Table(name = "wishes")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WishJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "project_title", length = 40)
    private String projectTitle;

    @Column(name = "project_thumbnail_url")
    private String projectThumbnailUrl;

    @Column(name = "snapshot_synced_at")
    private Instant snapshotSyncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = Instant.now();
    }
}
