package com.fundit.notification.infrastructure.persistence.notificationsetting;

import com.fundit.notification.infrastructure.persistence.notification.NotifType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NotificationSettingJpaRepository
        extends JpaRepository<NotificationSettingJpaEntity, NotificationSettingId> {

    /** NOTI-004 조회 — 이 회원이 수신 거부한 유형들(행 존재 = 거부). */
    List<NotificationSettingJpaEntity> findByMemberId(UUID memberId);

    /** 행이 존재하면 수신 거부다 — NOTI-006 적재 전 확인용. */
    boolean existsByMemberIdAndNotifType(UUID memberId, NotifType notifType);

    /**
     * NOTI-004 수신 거부 등록. 같은 설정을 두 번 꺼도 실패로 처리하지 않는다(idempotent).
     * @Modifying 쿼리는 호출부가 트랜잭션 안에 있어야 동작한다(NotificationSettingService의 @Transactional에 의존).
     */
    @Modifying
    @Query(value = "INSERT INTO notification_settings (member_id, notif_type) VALUES (:memberId, :notifType) "
            + "ON CONFLICT (member_id, notif_type) DO NOTHING", nativeQuery = true)
    void insertIgnoringConflict(@Param("memberId") UUID memberId, @Param("notifType") String notifType);

    /** NOTI-004 수신 재개. 이미 없는 대상 삭제도 정상(영향 행 0)으로 취급한다. */
    @Modifying
    @Query(value = "DELETE FROM notification_settings WHERE member_id = :memberId AND notif_type = :notifType",
            nativeQuery = true)
    void deleteByMemberIdAndNotifType(@Param("memberId") UUID memberId, @Param("notifType") String notifType);
}
