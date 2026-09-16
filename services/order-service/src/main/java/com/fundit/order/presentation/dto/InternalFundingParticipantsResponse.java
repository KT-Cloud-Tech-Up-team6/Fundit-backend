package com.fundit.order.presentation.dto;

import java.util.List;
import java.util.UUID;

/** 내부 전용 — fulfillment-service의 알림 팬아웃 대상(펀딩 성립 참여자) 조회 응답. */
public record InternalFundingParticipantsResponse(List<UUID> memberIds) {
}
