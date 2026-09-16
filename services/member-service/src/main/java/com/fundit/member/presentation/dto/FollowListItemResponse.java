package com.fundit.member.presentation.dto;

import java.time.Instant;
import java.util.UUID;

public record FollowListItemResponse(UUID sellerId, String sellerName, String sellerNickname, Instant createdAt) {
}
