package com.fundit.project.presentation.dto;

import java.util.List;

/** PROJECT-019 응답은 페이지네이션 없이 content 배열만 갖는다(API 명세서 #33 참고). */
public record LiveVerificationListResponse(List<LiveVerificationListItemResponse> content) {
}
