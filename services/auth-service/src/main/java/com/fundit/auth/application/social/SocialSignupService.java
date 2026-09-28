package com.fundit.auth.application.social;

import com.fundit.auth.application.signup.MemberServiceClient;
import com.fundit.auth.application.token.TokenIssuer;
import com.fundit.auth.domain.AuthErrorCode;
import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.auth.domain.account.Role;
import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.DependencyFailureException;
import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 소셜 회원가입(AUTH-008). 계정 생성 → member-service 동기 호출 → 실패 시 보상 삭제까지
 * {@code SignupService}와 같은 흐름이다. 다른 점은 비밀번호가 없고 소셜 식별자가 붙는다는 것뿐이라
 * member-service는 기존 {@code POST /api/v1/members}를 그대로 쓴다(요청 본문이 provider와 무관하다).
 *
 * <p>닉네임은 요청에 실려 온 값만 쓴다 — 제공자 닉네임은 프론트가 폼을 미리 채우는 용도이고
 * ({@code login/social} 응답의 {@code name}), 사용자가 고칠 수 있어야 하므로 서버가 덮어쓰지 않는다.
 * 값이 없으면 {@code @NotBlank}가 400으로 끊는다 — <b>실명으로 대신 채우지 않는다.</b>
 * 닉네임 칸에 실명이 들어가면 공개 화면에 실명이 나간다(security.md S9).
 *
 * <p><b>본인인증을 하지 않는다</b>(#150, 2026-09-24 결정 — 본인인증은 일반가입에만 남긴다).
 * 이름·전화번호는 요청값을 그대로 member 프로필에 넘기고, <b>계정의 이름·전화번호 해시는 비워 둔다.</b>
 * 인증되지 않은 값이 이메일 찾기(AUTH-009)·비밀번호 재설정·일반가입 중복 판정에 섞이면 안 된다 —
 * 남의 이름+번호를 넣어 소셜 가입하는 것만으로 그 사람의 일반가입을 막거나 이메일 찾기 결과를 오염시킬 수 있다.
 * 정책 A(연동)는 기존 일반 계정의 인증된 번호를 대조하므로 영향이 없다.
 */
@Service
@RequiredArgsConstructor
public class SocialSignupService {

    private final AccountRepository accountRepository;
    private final EmailConflictChecker emailConflictChecker;
    private final SocialTokenStore signupTokenStore;
    private final MemberServiceClient memberServiceClient;
    private final TokenIssuer tokenIssuer;

    public SocialSignupResult signup(SocialSignupCommand command) {
        var pending = signupTokenStore.consumeSignup(command.signupToken())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.TOKEN_EXPIRED));

        // 이미 연동된 계정이 있으면 가입이 아니라 로그인해야 한다(명세 AUTH-008).
        // signupToken 발급과 이 시점 사이에 다른 탭에서 가입이 끝났을 수 있다.
        if (accountRepository.findBySocial(pending.provider(), pending.socialId()).isPresent()) {
            throw new BusinessException(AuthErrorCode.SOCIAL_ACCOUNT_EXISTS,
                    "이미 " + pending.provider() + " 소셜 로그인 계정이 존재합니다.",
                    Map.of("provider", pending.provider()));
        }

        // 이메일은 제공자 값이 우선이다 — 로그인 식별자라, 사용자가 다른 값을 넣으면
        // 소셜 계정과 로그인 이메일이 갈라진다(요구사항 1.3.4 "제공되는 범위 내 연동").
        // 제공자가 안 준 경우(카카오는 비즈니스 앱 전환 없이는 불가)에만 입력값을 쓰고,
        // 그때 비로소 충돌 판정이 가능해진다 — 로그인 시점에는 이메일이 없어 못 했다.
        String email = pending.email() != null ? pending.email() : command.email();
        if (email == null || email.isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "이메일이 필요합니다.");
        }
        if (emailConflictChecker.checkOrThrow(email) != null) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        Instant now = Instant.now();
        Account account = Account.builder()
                .id(UuidCreator.getTimeOrderedEpoch())
                .email(email)
                // verifiedName/verifiedPhoneNumber 없음 — 본인인증을 안 거친 값이라 해시를 남기지 않는다(클래스 주석)
                // passwordHash 없음 — 소셜 전용 계정은 NULL 허용(V1__init_schema.sql)
                .socialProvider(pending.provider().name())
                .socialId(pending.socialId())
                .role(Role.MEMBER)
                .failedLoginCount(0)
                .mustChangePassword(false)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // SignupService와 같은 이유로 @Transactional로 감싸지 않는다 — member-service 동기 호출
        // 중에 DB 트랜잭션을 열어두지 않기 위해 save()가 독립 트랜잭션으로 즉시 커밋되어야 한다.
        account = accountRepository.save(account);

        MemberServiceClient.MemberProfile memberProfile;
        try {
            memberProfile = memberServiceClient.createProfile(new MemberServiceClient.CreateMemberProfileCommand(
                    account.getId(), email, command.name(), command.nickname(),
                    command.phoneNumber(), command.agreedTerms(), command.address()));
        } catch (DependencyFailureException e) {
            accountRepository.deleteById(account.getId());
            throw e;
        }

        var tokens = tokenIssuer.issue(account.getId(), account.getRole());
        return new SocialSignupResult(
                account.getId(), memberProfile.memberId(), tokens.accessToken(), tokens.refreshToken());
    }

    public record SocialSignupCommand(
            String signupToken,
            String email,
            String name,
            String nickname,
            String phoneNumber,
            List<String> agreedTerms,
            Map<String, Object> address
    ) {
    }

    public record SocialSignupResult(UUID accountId, UUID memberId, String accessToken, String refreshToken) {
    }
}
