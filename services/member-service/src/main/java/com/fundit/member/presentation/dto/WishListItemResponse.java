package com.fundit.member.presentation.dto;

import java.time.Instant;
import java.util.UUID;

/** {@code projectId}(숫자)는 찜 등록·해제용, {@code projectPublicId}는 프로젝트 상세 조회용이다. */
public record WishListItemResponse(Long projectId, UUID projectPublicId, String projectTitle,
                                   String projectThumbnailUrl, Instant createdAt) {
}
