package com.fundit.member.presentation.dto;

import java.util.UUID;

public record FollowResponse(UUID sellerId, boolean following) {
}
