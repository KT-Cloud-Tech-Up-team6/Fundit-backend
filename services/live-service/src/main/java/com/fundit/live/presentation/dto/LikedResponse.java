package com.fundit.live.presentation.dto;

/** 내가 이 LIVE에 좋아요를 눌렀는지(FE #269). 로그인 사용자 전용이라 인증이 필수다. */
public record LikedResponse(boolean liked) {
}
