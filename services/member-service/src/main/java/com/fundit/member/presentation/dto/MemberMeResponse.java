package com.fundit.member.presentation.dto;

import java.util.UUID;

/** currentMode 필드 없음 — 모드 전환 기능 자체가 없음(사용자 확인, 2026-09-03). */
public record MemberMeResponse(UUID memberId, String name, String nickname, String phoneNumber, boolean isSeller, boolean isBuyer) {
}
