package com.fundit.notification.infrastructure.persistence.notificationsetting;

import com.fundit.notification.infrastructure.persistence.notification.NotifType;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * notification_settings의 복합 PK (member_id, notif_type).
 *
 * <p>record로 쓸 수 없다 — @IdClass는 기본 생성자를 요구하는데 record에는 없다.
 * 대리키 id를 두지 않는 이유는 id로 조회할 일이 없어서다(자연키를 PK로 쓰면 인덱스 2개가 1개가 된다).
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class NotificationSettingId implements Serializable {

    private java.util.UUID memberId;
    private NotifType notifType;
}
