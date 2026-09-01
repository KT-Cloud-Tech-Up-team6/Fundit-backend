package com.fundit.project.presentation.dto.common;

import java.util.List;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements) {
}
