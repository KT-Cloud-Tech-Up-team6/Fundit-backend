package com.fundit.project.presentation.dto;

import java.util.UUID;

/** 프로젝트 공개(발행, PROJECT-029) 응답. */
public record ProjectStatusResponse(UUID projectId, String status) {
}
