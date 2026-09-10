package com.fundit.member.presentation.controller;

import com.fundit.member.application.member.MemberQueryService;
import com.fundit.member.application.member.MemberSignupService;
import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.member.presentation.dto.MemberCreateRequest;
import com.fundit.member.presentation.dto.MemberCreateResponse;
import com.fundit.member.presentation.dto.MemberMeResponse;
import com.fundit.member.presentation.dto.PhoneVerificationRequest;
import com.fundit.member.presentation.dto.PhoneVerificationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    /**
     * 내부 전용 — auth-service의 회원가입만 호출한다. 두 겹으로 막는다:
     * ① 게이트웨이가 이 경로+메서드를 404로 끊어 외부 노출을 차단하고,
     * ② InternalGatewaySecretFilter가 X-Internal-Api-Key로 게이트웨이 우회 직접 호출을 차단한다.
     */
    @PostMapping
    public MemberCreateResponse create(@Valid @RequestBody MemberCreateRequest request) {
        var result = memberSignupService.signup(new MemberSignupService.SignupCommand(
                request.accountId(), request.name(), request.nickname(), request.phoneNumber(),
                request.agreedTerms(), request.address()));
        return new MemberCreateResponse(result.memberId(), null, true, true, result.createdAt());
    }

    /**
     * 내부 전용 — auth-service의 소셜 계정 연동만 호출한다. 위 create와 같은 두 겹 방어를 받는다.
     *
     * <p>번호로 계정을 찾아주는 게 아니라 <b>이미 아는 계정에 대해 맞는지만</b> 답한다.
     * 반대 방향(번호 → 계정)이었다면 번호만 넣어보며 가입 여부를 캐낼 수 있다.
     */
    @PostMapping("/{accountId}/phone-verification")
    public PhoneVerificationResponse verifyPhone(
            @PathVariable UUID accountId, @Valid @RequestBody PhoneVerificationRequest request) {
        return new PhoneVerificationResponse(memberQueryService.phoneMatches(accountId, request.phoneNumber()));
    }

    @GetMapping("/me")
    public MemberMeResponse getMe(@LoginUser CurrentUser user) {
        var profile = memberQueryService.getMe(user.id());
        return new MemberMeResponse(profile.memberId(), profile.name(), profile.nickname(), profile.phoneNumber(), true, true);
    }
}
