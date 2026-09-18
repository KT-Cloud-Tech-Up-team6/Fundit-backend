package com.fundit.live.presentation.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** projectId는 project-service의 public_id(UUID)다. */
public record LiveCreateRequest(@NotNull UUID projectId) {
}
