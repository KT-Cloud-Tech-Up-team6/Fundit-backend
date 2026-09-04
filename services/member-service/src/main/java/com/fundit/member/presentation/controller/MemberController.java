package com.fundit.member.presentation.controller;

import com.fundit.member.application.member.MemberQueryService;
import com.fundit.member.application.member.MemberSignupService;
import com.fundit.member.infrastructure.security.CurrentMember;
import com.fundit.member.presentation.dto.MemberCreateRequest;
import com.fundit.member.presentation.dto.MemberCreateResponse;
import com.fundit.member.presentation.dto.MemberMeResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberSignupService memberSignupService;
    private final MemberQueryService memberQueryService;

    /** 내부 전용 — auth-service만 호출(InternalApiKeyFilter로 방어, 게이트웨이 라우팅에서도 제외 예정). */
    @PostMapping
    public MemberCreateResponse create(@Valid @RequestBody MemberCreateRequest request) {
        var result = memberSignupService.signup(new MemberSignupService.SignupCommand(
                request.accountId(), request.name(), request.phoneNumber(),
                request.agreedTerms(), request.address()));
        return new MemberCreateResponse(result.memberId(), null, true, true, result.createdAt());
    }

    @GetMapping("/me")
    public MemberMeResponse getMe(@CurrentMember UUID accountId) {
        var profile = memberQueryService.getMe(accountId);
        return new MemberMeResponse(profile.memberId(), profile.name(), profile.nickname(), profile.phoneNumber(), true, true);
    }
}
