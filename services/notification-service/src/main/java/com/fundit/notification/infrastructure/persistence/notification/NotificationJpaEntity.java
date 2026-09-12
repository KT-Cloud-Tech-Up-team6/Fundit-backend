package com.fundit.notification.infrastructure.persistence.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 단순 애그리거트(persistence-convention.md §2) — 값 저장·조회와 read_at 1회 기록이 전부라
 * domain/Mapper/Adapter 없이 application이 이 JpaEntity를 직접 사용한다.
 *
 * <p>body 컬럼은 없다 — 알림함이 제목 한 줄이라는 전제로 뺐다(프론트가 2줄 레이아웃이면 되살릴 것).
 * updated_at도 없다 — 유일한 변경이 read_at이고 그 자체가 시각이다.
 */
@Getter
@Entity
@Builder
@Table(name = "notifications")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 발행 측이 싣는 이벤트 고유 ID. (event_id, member_id) UNIQUE가 중복 소비를 막는 유일한 근거다. */
    @Column(name = "event_id", nullable = false, length = 64, updatable = false)
    private String eventId;

    /** 수신자. member-service 참조이며 FK가 아니다. */
    @Column(name = "member_id", nullable = false, updatable = false)
    private UUID memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notif_type", nullable = false, length = 30, updatable = false)
    private NotifType notifType;

    @Column(name = "title", nullable = false, length = 100, updatable = false)
    private String title;

    @Column(name = "related_url", nullable = false, updatable = false)
    private String relatedUrl;

    /** null이면 안 읽음. 최초 1회만 기록하고 덮어쓰지 않는다(NOTI-005 idempotent). */
    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = Instant.now();
    }

    /** 이미 읽은 알림은 기존 시각을 유지한다 — 재호출을 실패로 처리하지도, 덮어쓰지도 않는다. */
    public void markRead(Instant now) {
        if (this.readAt == null) this.readAt = now;
    }
}
