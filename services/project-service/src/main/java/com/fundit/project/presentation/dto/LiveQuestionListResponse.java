package com.fundit.project.presentation.dto;

import java.util.List;

/** LIVE검증 목록과 동일하게 페이지네이션 없이 content 배열만 갖는다. */
public record LiveQuestionListResponse(List<LiveQuestionListItemResponse> content) {
}
