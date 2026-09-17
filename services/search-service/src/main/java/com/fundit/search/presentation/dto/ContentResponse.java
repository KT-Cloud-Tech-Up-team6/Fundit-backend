package com.fundit.search.presentation.dto;

import java.util.List;

/** 페이지네이션 없는 단일 목록 응답 공통 포맷({@code {"content": [...]}}). */
public record ContentResponse<T>(List<T> content) {
}
