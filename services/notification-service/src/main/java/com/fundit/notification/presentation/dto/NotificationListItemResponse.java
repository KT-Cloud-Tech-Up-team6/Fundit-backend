package com.fundit.notification.presentation.dto;

import com.fundit.notification.infrastructure.persistence.notification.NotifType;

import java.time.Instant;

/** NOTI-003 목록 항목. readAt이 null이면 안 읽은 알림이다(항목별 표시용). */
public record NotificationListItemResponse(Long notificationId, NotifType notifType, String title,
                                           String relatedUrl, Instant readAt, Instant createdAt) {
}
