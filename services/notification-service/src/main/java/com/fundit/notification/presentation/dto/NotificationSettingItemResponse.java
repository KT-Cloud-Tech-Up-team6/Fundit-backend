package com.fundit.notification.presentation.dto;

import com.fundit.notification.infrastructure.persistence.notification.NotifType;

/** NOTI-004 조회 — 유형별 현재 수신 여부. */
public record NotificationSettingItemResponse(NotifType notifType, boolean enabled) {
}
