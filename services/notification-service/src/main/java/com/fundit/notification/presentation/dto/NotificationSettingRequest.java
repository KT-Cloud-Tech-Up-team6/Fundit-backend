package com.fundit.notification.presentation.dto;

import com.fundit.notification.infrastructure.persistence.notification.NotifType;
import jakarta.validation.constraints.NotNull;

/**
 * NOTI-004 수신설정 변경 요청.
 *
 * <p>notifType을 String이 아니라 NotifType으로 받는다 — 정의되지 않은 값은 Jackson이 거르고
 * AbstractGlobalExceptionHandler가 400(INVALID_INPUT)으로 매핑하므로 검증 분기를 따로 쓰지 않는다.
 *
 * @param enabled false면 해당 유형 수신 거부. Boolean(박싱)인 이유는 boolean이면 누락 시 false로
 *                조용히 채워져 @NotNull이 잡지 못하기 때문이다.
 */
public record NotificationSettingRequest(@NotNull NotifType notifType, @NotNull Boolean enabled) {
}
