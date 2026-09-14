package com.fundit.notification.presentation.dto;

/** NOTI-007 안읽음 개수. 알림함 밖(홈 등)에서도 호출되므로 목록 응답에 얹지 않고 별도로 둔다. */
public record UnreadCountResponse(long unreadCount) {
}
