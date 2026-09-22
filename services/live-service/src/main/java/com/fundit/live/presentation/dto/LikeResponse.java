package com.fundit.live.presentation.dto;

/** PUT/DELETE 좋아요 응답 — 204에서 전환. FE가 낙관적 업데이트 후 재조회하지 않아도 된다. */
public record LikeResponse(boolean liked, int likeCount) {
}
