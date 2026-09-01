package com.fundit.project.presentation.dto.notice;

import jakarta.validation.constraints.NotBlank;

public record NoticeCreateRequest(@NotBlank String noticeType,
                                  @NotBlank String title,
                                  @NotBlank String content) {
}
