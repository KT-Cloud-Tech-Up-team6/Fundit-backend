package com.fundit.notification.presentation.dto;

/** NOTI-002 신청 상태. PUT이면 true, DELETE면 false. */
public record LiveNotifyResponse(boolean notifying) {
}
