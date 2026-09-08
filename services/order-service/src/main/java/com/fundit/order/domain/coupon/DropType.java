package com.fundit.order.domain.coupon;

/** LIVE 채널 쿠폰에만 사용(issue_channel=LIVE). GENERAL 쿠폰은 null. */
public enum DropType {
    FIRST_COME, MANUAL_DROP, WATCH_TIME_AUTO
}
