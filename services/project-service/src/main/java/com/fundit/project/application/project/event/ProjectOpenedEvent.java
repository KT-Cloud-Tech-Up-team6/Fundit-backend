package com.fundit.project.application.project.event;

import java.util.List;
import java.util.UUID;

/** 검수 승인 커밋 후 오픈 알림을 보내기 위한 이벤트. */
public record ProjectOpenedEvent(Long projectId, List<UUID> memberIds) {

    public ProjectOpenedEvent {
        memberIds = List.copyOf(memberIds);
    }
}
