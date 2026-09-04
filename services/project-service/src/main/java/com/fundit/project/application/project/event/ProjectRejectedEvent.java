package com.fundit.project.application.project.event;

import java.util.UUID;

/** 검수 반려 커밋 후 판매자 알림을 보내기 위한 이벤트. */
public record ProjectRejectedEvent(Long projectId, UUID sellerId, String rejectReason) {
}
