package com.fundit.project.presentation.dto;

import java.util.UUID;

public record PastProjectResponse(UUID projectId, String title, String status) {
}
