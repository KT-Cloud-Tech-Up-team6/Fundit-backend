package com.fundit.order.domain.funding;

public enum FundingStatus {
    PENDING,
    FUNDING_IN_PROGRESS,
    CANCELLED_BY_MEMBER,
    PAYMENT_EXPIRED,
    GOAL_FAILED_REFUNDED,
    GOAL_ACHIEVED,
    REFUNDED_AFTER_SUCCESS
}
