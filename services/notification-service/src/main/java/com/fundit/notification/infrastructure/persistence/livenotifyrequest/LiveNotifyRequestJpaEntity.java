package com.fundit.notification.infrastructure.persistence.livenotifyrequest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * LIVE 시작 알림 신청(NOTI-002). 단순 애그리거트 — 행의 존재 자체가 신청 상태다.
 *
 * <p>소유 서비스는 미결이다: ERD는 streaming.live_notify_requests를 live-service DB에 두고 있으나
 * live-service는 개발 착수 전이고, 실제로 알림을 발송하는 주체가 notification-service다.
 * 반대로 두면 발송할 때마다 live-service에 "누가 신청했나"를 물어야 해서 홉이 하나 늘고,
 * 그 서비스가 죽으면 알림도 못 나간다. live-service 착수 시점에 ERD 담당자와 확정한다.
 */
@Getter
@Entity
@Builder
@Table(name = "live_notify_requests")
@IdClass(LiveNotifyRequestId.class)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveNotifyRequestJpaEntity {

    /** live-service 참조, FK 아님. 타입은 ERD 미확인 상태의 가정(UUID). */
    @Id
    @Column(name = "live_id", nullable = false)
    private UUID liveId;

    @Id
    @Column(name = "member_id", nullable = false)
    private UUID memberId;
}
