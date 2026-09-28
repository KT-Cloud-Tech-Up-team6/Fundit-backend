package com.fundit.member.presentation.controller;

import com.fundit.member.application.member.MemberQueryService;
import com.fundit.member.presentation.dto.MemberNicknameResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 서비스 간 호출 전용. 보호는 {@code InternalEndpointConfig}의 {@code InternalEndpoint} 빈이 담당하고,
 * {@code /internal/**}은 게이트웨이 라우트에 없어 외부로 열리지 않는다.
 */
@RestController
@RequiredArgsConstructor
public class InternalMemberController {

    private final MemberQueryService memberQueryService;

    /** 판매자명 표시용 닉네임 일괄 조회(live-service 목록 카드). 없는 id는 응답에서 빠진다. */
    @GetMapping("/internal/v1/members/nicknames")
    public List<MemberNicknameResponse> nicknames(@RequestParam(required = false) List<UUID> ids) {
        return memberQueryService.findNicknames(ids).stream()
                .map(MemberNicknameResponse::from)
                .toList();
    }
}
