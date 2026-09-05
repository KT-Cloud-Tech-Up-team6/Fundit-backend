package com.fundit.project.presentation.dto;

import java.util.UUID;

public record ProjectCreateResponse(UUID projectId, String status) {
}
