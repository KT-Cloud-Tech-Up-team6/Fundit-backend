package com.fundit.member.infrastructure.persistence.event;

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
 * 단순 애그리거트 — member-service가 발행하는 이벤트의 트랜잭셔널 아웃박스 행.
 *
 * <p>서비스당 아웃박스 한 벌이 레포 관행이라 찜(MEMBER-005)과 가입(MEMBER-002)이 한 테이블을 쓴다
 * (order {@code funding_event_outbox}가 같은 형태다). {@code projectId}는 찜 이벤트만 채우고
 * 가입 이벤트에는 없어 nullable이다.
 */
@Getter
@Entity
@Builder
@Table(name = "member_event_outbox")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberEventOutboxJpaEntity {

    public static final String TYPE_WISHED = "PROJECT_WISHED";
    public static final String TYPE_UNWISHED = "PROJECT_UNWISHED";
    public static final String TYPE_SIGNED_UP = "MEMBER_SIGNED_UP";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    /** 찜 이벤트만 채운다. 가입 이벤트에는 없다. */
    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Builder.Default
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "last_error")
    private String lastError;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = Instant.now();
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
        this.lastError = null;
    }

    public void recordFailure(String error) {
        this.attemptCount += 1;
        this.lastError = error;
    }
}
