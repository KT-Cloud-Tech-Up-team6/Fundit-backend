package com.fundit.member.infrastructure.persistence.follow;

import java.time.Instant;
import java.util.UUID;

/** 팔로우 목록 한 줄 — follows와 members를 조인한 조회 전용 값. */
public record FollowView(UUID sellerId, String sellerName, String sellerNickname, Instant createdAt) {
}
