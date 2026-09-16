package com.fundit.member.infrastructure.persistence.follow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
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
 * 메이커 팔로우(MEMBER-007). 단순 애그리거트(persistence-convention.md §2) —
 * 행의 존재 자체가 팔로우 상태이고 상태 전이가 없다.
 *
 * <p>wishes와 달리 판매자 이름 같은 스냅샷 컬럼을 두지 않는다. 팔로우 대상은 같은 DB의
 * members 한 행이라 조회 시 조인하면 되고, 복사해두면 동기화 문제만 새로 생긴다.
 */
@Getter
@Entity
@Builder
@Table(name = "follows")
@IdClass(FollowId.class)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FollowJpaEntity {

    @Id
    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Id
    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = Instant.now();
    }
}
