package com.fundit.member.infrastructure.persistence.wish;

import java.time.Instant;
import java.util.UUID;

/** 찜 목록 한 줄 — wishes와 project_snapshots를 조인한 조회 전용 값. 스냅샷이 없으면 프로젝트 필드는 null. */
public record WishView(Long projectId, UUID projectPublicId, String projectTitle, String projectThumbnailUrl,
                       Instant createdAt) {
}
