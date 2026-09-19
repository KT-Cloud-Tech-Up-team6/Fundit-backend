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
 *
 * <p><b>@Builder·@AllArgsConstructor를 두지 않는다.</b> 이 엔티티를 JPA로 생성하는 코드가 없다 —
 * 삽입은 {@code ON CONFLICT DO NOTHING} 네이티브 쿼리가 하고, 그래야 동시 요청에서
 * 중복이 흡수된다. 쓰이지 않는 생성자를 만들면 "이걸로도 저장할 수 있다"는 잘못된 신호가 된다.
 *
 * <p>같은 이유로 {@code createdAt}에 @PrePersist를 걸지 않는다 — 네이티브 INSERT는 JPA
 * 생명주기를 타지 않으므로 DB 기본값(now())이 유일하게 동작하는 방법이다.
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
