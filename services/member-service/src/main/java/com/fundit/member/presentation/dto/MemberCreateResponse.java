package com.fundit.member.presentation.dto;

import java.time.Instant;
import java.util.UUID;

public record MemberCreateResponse(UUID memberId, String nickname, boolean isSeller, boolean isBuyer, Instant createdAt) {
}
