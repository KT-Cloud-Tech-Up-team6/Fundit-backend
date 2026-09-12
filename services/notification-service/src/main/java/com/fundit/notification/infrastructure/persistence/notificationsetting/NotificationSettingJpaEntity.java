package com.fundit.notification.infrastructure.persistence.notificationsetting;

import com.fundit.notification.infrastructure.persistence.notification.NotifType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 수신 거부 목록. <b>행이 존재하면 그 유형을 받지 않는다.</b>
 *
 * <p>enabled 컬럼을 두지 않는 이유: enabled=true 행은 기본값과 같은 값을 저장한 무의미한 행이다.
 * 기본값이 "행 없음 = 전체 수신"이라 신규 회원 초기 데이터가 필요 없고, 설정 변경이 insert/delete로 끝난다.
 * channel 축도 두지 않는다 — MVP 채널이 온사이트 하나뿐이라 단일값만 들어가는 분기다.
 *
 * <p>@EmbeddedId가 아니라 @IdClass인 이유: 파생 쿼리 프로퍼티 경로가 평평하게 유지된다
 * (existsByMemberIdAndNotifType vs existsById_MemberIdAndId_NotifType).
 *
 * <p>단순 애그리거트(persistence-convention.md §0 복잡도 표에 NotificationSetting이 예시로 등재돼 있다).
 */
@Getter
@Entity
@Builder
@Table(name = "notification_settings")
@IdClass(NotificationSettingId.class)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSettingJpaEntity {

    @Id
    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "notif_type", nullable = false, length = 30)
    private NotifType notifType;
}
