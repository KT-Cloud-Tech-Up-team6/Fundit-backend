package com.fundit.member.presentation.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** api-convention.md 페이지네이션 응답 규격. */
public record PageResponse<T>(
        List<T> content, int page, int size, long totalElements, int totalPages, boolean hasNext
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }
}
