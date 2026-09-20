package com.fundit.auth.presentation.dto;

import com.fundit.auth.presentation.dto.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;

/** 메일 링크로 새 비밀번호를 설정한다(AUTH-010). 복잡도 규칙은 가입·변경과 동일하다. */
public record PasswordResetConfirmRequest(
        @NotBlank String token,
        @NotBlank @ValidPassword String newPassword
) {
}
