package com.fundit.member.presentation.controller;

import com.fundit.member.application.member.MemberQueryService;
import com.fundit.member.application.member.MemberSignupService;
import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
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


@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberSignupService memberSignupService;
    private final MemberQueryService memberQueryService;

    /**
     * 내부 전용 — auth-service의 회원가입만 호출한다. 두 겹으로 막는다:
     * ① 게이트웨이가 이 경로+메서드를 404로 끊어 외부 노출을 차단하고,
     * ② InternalGatewaySecretFilter가 X-Internal-Api-Key로 게이트웨이 우회 직접 호출을 차단한다.
     */
    @PostMapping
    public MemberCreateResponse create(@Valid @RequestBody MemberCreateRequest request) {
        var result = memberSignupService.signup(new MemberSignupService.SignupCommand(
                request.accountId(), request.name(), request.phoneNumber(),
                request.agreedTerms(), request.address()));
        return new MemberCreateResponse(result.memberId(), null, true, true, result.createdAt());
    }

    @GetMapping("/me")
    public MemberMeResponse getMe(@LoginUser CurrentUser user) {
        var profile = memberQueryService.getMe(user.id());
        return new MemberMeResponse(profile.memberId(), profile.name(), profile.nickname(), profile.phoneNumber(), true, true);
    }
}
