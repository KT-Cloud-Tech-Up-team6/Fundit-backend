package com.fundit.live.infrastructure.persistence.like;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 행의 존재 자체가 좋아요 상태다. 쓰기는 전부 네이티브 쿼리(idempotent)로 하고
 * 이 엔티티는 JpaRepository 타입 파라미터를 채우기 위해 둔다.
 */
@Getter
@Entity
@IdClass(LiveLikeId.class)
@Table(name = "live_likes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveLikeJpaEntity {

    @Id
    @Column(name = "session_id")
    private Long sessionId;

    @Id
    @Column(name = "member_id")
    private UUID memberId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
