package com.fundit.live.infrastructure.persistence.event;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * 아웃박스. 도메인 변경과 같은 트랜잭션에서 적재되므로 "방송은 끝났는데 이벤트만 사라지는"
 * 경우가 없다.
 *
 * <p>payload가 JSONB인 이유: 질문요약 이벤트의 payload가 배열이라 컬럼으로 펼 수 없다.
 */
@Getter
@Entity
@Builder
@Table(name = "live_event_outbox")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveEventOutboxJpaEntity {

    public static final String TYPE_LIVE_ENDED = "LIVE_ENDED";
    public static final String TYPE_QUESTIONS_SUMMARIZED = "LIVE_QUESTIONS_SUMMARIZED";
    /**
     * 방송 시작. notification.raised.v1이 아니라 도메인 토픽을 쓴다 —
     * 그쪽은 수신자가 채워져 있어야 하는데 신청자 목록은 notification이 소유한다.
     */
    public static final String TYPE_LIVE_STARTED = "LIVE_STARTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(name = "live_session_id", nullable = false, updatable = false)
    private Long liveSessionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false)
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error")
    private String lastError;

    /** <b>전송이 확인된 뒤에만</b> 호출해야 한다 — 먼저 부르면 발행된 척 행이 사라진다. */
    public void markPublished() {
        this.publishedAt = Instant.now();
        this.lastError = null;
    }

    public void recordFailure(String error) {
        this.attemptCount += 1;
        this.lastError = error;
    }
}
