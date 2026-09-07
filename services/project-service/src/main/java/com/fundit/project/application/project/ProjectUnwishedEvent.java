package com.fundit.project.application.project;

import java.util.UUID;

/** member-service가 발행하는 찜 해제 이벤트(PROJECT-016). */
public record ProjectUnwishedEvent(Long projectId, UUID memberId) {
}
