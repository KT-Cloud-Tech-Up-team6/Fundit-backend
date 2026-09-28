package com.fundit.member.presentation.dto;

import com.fundit.member.application.member.MemberQueryService;

import java.util.UUID;

public record MemberNicknameResponse(UUID memberId, String nickname) {

    public static MemberNicknameResponse from(MemberQueryService.MemberNickname n) {
        return new MemberNicknameResponse(n.memberId(), n.nickname());
    }
}
