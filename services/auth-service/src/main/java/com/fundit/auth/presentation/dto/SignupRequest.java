package com.fundit.auth.presentation.dto;

import com.fundit.auth.presentation.dto.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

/**
 * verificationToken(휴대폰 본인인증 임시토큰)은 필드만 받고 검증하지 않는다(사용자 확정,
 * AUTH-004/005 미구현). AuthController가 SignupService.SignupCommand로 매핑할 때 이 필드를
 * 그냥 버린다.
 */
public record SignupRequest(
        @NotBlank @ValidPassword String password,
        @NotBlank @Email String email,
        String verificationToken,
        @NotBlank String name,
        @NotBlank String phoneNumber,
        @NotEmpty List<String> agreedTerms,
        Map<String, Object> address
) {
}
