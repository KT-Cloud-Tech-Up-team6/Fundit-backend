package com.fundit.auth.presentation.dto;

import com.fundit.auth.presentation.dto.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record SignupRequest(
        @NotBlank @ValidPassword String password,
        @NotBlank @Email String email,
        @NotBlank String verificationToken,
        @NotBlank String name,
        @NotBlank @Size(max = 50) String nickname,
        @NotBlank String phoneNumber,
        @NotEmpty List<String> agreedTerms,
        Map<String, Object> address
) {
}
