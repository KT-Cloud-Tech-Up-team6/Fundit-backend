package com.fundit.project.presentation.dto.community;

import com.fundit.project.domain.community.PostType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CommunityPostCreateRequest(@NotNull PostType postType, @NotBlank String content) {
}
