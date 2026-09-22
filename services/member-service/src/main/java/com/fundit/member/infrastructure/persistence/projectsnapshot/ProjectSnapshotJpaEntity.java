package com.fundit.member.infrastructure.persistence.projectsnapshot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 찜 목록 표시용 프로젝트 스냅샷(조회 전용). 쓰기는 {@link ProjectSnapshotJpaRepository#upsert}만 한다 —
 * 이벤트가 재전송·역순으로 와도 옛 값이 최신 값을 덮지 않게 DB가 sourceVersion을 비교한다.
 */
@Getter
@Entity
@Table(name = "project_snapshots")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectSnapshotJpaEntity {

    @Id
    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "project_public_id", nullable = false)
    private UUID projectPublicId;

    @Column(name = "title")
    private String title;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "source_version")
    private Long sourceVersion;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;
}
