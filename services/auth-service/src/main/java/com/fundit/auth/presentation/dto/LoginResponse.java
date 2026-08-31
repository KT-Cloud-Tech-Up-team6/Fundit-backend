package com.fundit.auth.presentation.dto;

/** 사용자 확정 사항: member.nickname은 생략(member-service 미도입, 로그인 경로에 불필요한 동기 결합 방지). */
public record LoginResponse(String accessToken, boolean mustChangePassword) {
}
