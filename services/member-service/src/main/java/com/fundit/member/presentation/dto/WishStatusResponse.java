package com.fundit.member.presentation.dto;

import java.util.UUID;

/** 프로젝트 상세(UUID 기준) 찜 상태 — 숫자 id를 쓰는 {@link WishResponse}와 식별자 타입이 달라 따로 둔다. */
public record WishStatusResponse(UUID projectPublicId, boolean wished) {
}
