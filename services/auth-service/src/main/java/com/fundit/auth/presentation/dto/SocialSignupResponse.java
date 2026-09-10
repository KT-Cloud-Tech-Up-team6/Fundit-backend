package com.fundit.auth.presentation.dto;

import java.util.UUID;

public record SocialSignupResponse(UUID accountId, UUID memberId, String accessToken) {
}
