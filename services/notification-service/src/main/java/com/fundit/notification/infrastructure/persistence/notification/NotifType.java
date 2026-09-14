package com.fundit.notification.infrastructure.persistence.notification;

/**
 * 알림 유형 화이트리스트(NotificationFunctionalSpec.md "알림 유형" 표).
 *
 * <p>DB에는 CHECK 제약을 두지 않는다 — 레포가 열거값에 CHECK를 쓰지 않는 관행
 * (accounts.role, reward_event_outbox.event_type)을 따르고 값 검증은 앱이 한다.
 * 요청 DTO 필드를 이 타입으로 두면 정의되지 않은 값은 Jackson이 거르고
 * AbstractGlobalExceptionHandler가 400(INVALID_INPUT)으로 매핑하므로 검증 분기를 따로 쓰지 않는다.
 *
 * <p>PM 14.5.3의 배송 알림 3종(단계 변경/진행 내용 업데이트/일정 변경)은 SHIPPING_UPDATE 하나로 묶었다 —
 * 수신설정이 유형 단위라 3개로 쪼개면 "배송 알림 끄기"를 세 번 해야 한다. 세부 구분은 문구·relatedUrl로 처리한다.
 */
public enum NotifType {
    LIVE_START,
    COMMUNITY_ANSWER,
    SHIPPING_UPDATE,
    REFUND_STATUS,
    PROJECT_OPEN,
    REWARD_RESTOCK,
    COUPON_EXPIRING,
    SELLER_UPDATE_DUE
}
