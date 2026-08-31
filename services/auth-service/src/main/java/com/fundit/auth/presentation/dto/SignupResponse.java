package com.fundit.auth.presentation.dto;

import java.util.UUID;

public record SignupResponse(UUID accountId, UUID memberId, String accessToken) {
}
