package com.fundit.project.presentation.dto.common;

import java.util.List;

/** 페이징 없이 목록만 내려주는 응답의 공통 껍데기({"content": [...]}). */
public record ListResponse<T>(List<T> content) {

    public static <T> ListResponse<T> of(List<T> content) {
        return new ListResponse<>(content);
    }
}
