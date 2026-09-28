package com.fundit.auth.presentation.dto;

/** 마이페이지 이름 아래 줄에 보이는 이메일(PM 확정: 마스킹하지 않음). 이름·전화번호는 member {@code /me}가 준다. */
public record MyAccountResponse(String email) {
}
