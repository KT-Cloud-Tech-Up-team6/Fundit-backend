package com.fundit.project.presentation.dto;

import java.util.UUID;

/** 심사 제출(PROJECT-029)·심사 처리(PROJECT-030) 응답 — projectId/status만 반환하는 형태가 동일하다. */
public record ProjectStatusResponse(UUID projectId, String status) {
}
