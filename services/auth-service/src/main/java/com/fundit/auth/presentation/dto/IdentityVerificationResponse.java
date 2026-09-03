package com.fundit.auth.presentation.dto;

import java.time.Instant;

public record IdentityVerificationResponse(String verificationToken, Instant expiresAt) {
}
