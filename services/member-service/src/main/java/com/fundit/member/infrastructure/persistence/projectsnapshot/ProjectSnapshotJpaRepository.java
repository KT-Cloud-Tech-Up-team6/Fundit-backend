package com.fundit.member.infrastructure.persistence.projectsnapshot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface ProjectSnapshotJpaRepository extends JpaRepository<ProjectSnapshotJpaEntity, Long> {

    /**
     * 승인·수정 이벤트를 그대로 반영한다(멱등). 이미 더 최신 버전이 반영돼 있으면 무시한다 —
     * 재전송된 옛 이벤트가 최신 제목·썸네일을 덮으면 안 된다. 버전이 없는(구버전) 이벤트는 저장된 버전도
     * 없을 때만 반영한다 — 버전 있는 값을 null로 덮으면 이후 어떤 옛 이벤트든 다시 덮을 수 있게 된다.
     * 리스너는 트랜잭션 밖에서 부르므로 여기서 트랜잭션을 연다.
     */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO project_snapshots (project_id, project_public_id, title, thumbnail_url, source_version, synced_at)
            VALUES (:projectId, :publicId, :title, :thumbnailUrl, :sourceVersion, now())
            ON CONFLICT (project_id) DO UPDATE SET
                project_public_id = EXCLUDED.project_public_id,
                title = EXCLUDED.title,
                thumbnail_url = EXCLUDED.thumbnail_url,
                source_version = EXCLUDED.source_version,
                synced_at = now()
            WHERE project_snapshots.source_version IS NULL
               OR EXCLUDED.source_version >= project_snapshots.source_version
            """, nativeQuery = true)
    void upsert(@Param("projectId") Long projectId, @Param("publicId") UUID publicId,
                @Param("title") String title, @Param("thumbnailUrl") String thumbnailUrl,
                @Param("sourceVersion") Long sourceVersion);
}
