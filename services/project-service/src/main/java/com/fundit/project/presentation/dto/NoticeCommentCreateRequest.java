package com.fundit.project.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NoticeCommentCreateRequest(@NotBlank @Size(max = 500) String content) {
}
