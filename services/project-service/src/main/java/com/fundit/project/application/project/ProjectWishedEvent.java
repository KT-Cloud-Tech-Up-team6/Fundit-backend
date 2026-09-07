package com.fundit.project.application.project;

import java.util.UUID;

/** member-service가 발행하는 찜 등록 이벤트(PROJECT-016). */
public record ProjectWishedEvent(Long projectId, UUID memberId) {
}
