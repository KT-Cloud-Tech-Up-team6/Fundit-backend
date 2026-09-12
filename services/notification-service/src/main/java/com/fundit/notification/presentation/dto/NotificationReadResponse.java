package com.fundit.notification.presentation.dto;

import java.time.Instant;

/** NOTI-005 읽음 처리 결과. 이미 읽은 알림이면 기존 readAt이 그대로 담긴다. */
public record NotificationReadResponse(Long notificationId, Instant readAt) {
}
