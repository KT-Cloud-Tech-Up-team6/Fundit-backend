package com.fundit.auth.application.signup;

import com.fundit.auth.application.identity.IdentityVerificationStore;
import com.fundit.auth.application.token.TokenIssuer;
import com.fundit.auth.application.social.EmailConflictChecker;
import com.fundit.auth.domain.AuthErrorCode;
import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.auth.domain.account.Role;
import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.DependencyFailureException;
import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SignupService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final MemberServiceClient memberServiceClient;
    private final IdentityVerificationStore identityVerificationStore;
    private final TokenIssuer tokenIssuer;
    private final EmailConflictChecker emailConflictChecker;

    public SignupResult signup(SignupCommand command) {
        // 존재 여부만 보지 않고 계정을 꺼내는 이유(정책 B): 소셜로 가입된 이메일이면
        // "이미 가입됨"이 아니라 어느 제공자로 가입됐는지 알려줘야 사용자가 소셜 로그인으로 갈 수 있다.
        if (emailConflictChecker.checkOrThrow(command.email()) != null) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        var verifiedIdentity = identityVerificationStore.consume(command.verificationToken())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.TOKEN_INVALID));
        if (!verifiedIdentity.phoneNumber().equals(command.phoneNumber())
                || !verifiedIdentity.name().equals(command.name())) {
            throw new BusinessException(CommonErrorCode.TOKEN_INVALID);
        }

        Instant now = Instant.now();
        Account account = Account.builder()
                .id(UuidCreator.getTimeOrderedEpoch())
                .email(command.email())
                .passwordHash(passwordEncoder.encode(command.password()))
                .role(Role.MEMBER)
                .failedLoginCount(0)
                .mustChangePassword(false)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // 이 메서드 전체를 @Transactional로 감싸지 않는다 — save() 호출 자체가 독립 트랜잭션으로
        // 즉시 커밋되어야, 아래 member-service 동기 호출 중에 DB 트랜잭션을 열어두지 않는다
        // (AuthDomainApiSpec.md AUTH-007의 보상 트랜잭션 패턴, CLAUDE.md 핵심 설계 결정).
        account = accountRepository.save(account);

        MemberServiceClient.MemberProfile memberProfile;
        try {
            memberProfile = memberServiceClient.createProfile(new MemberServiceClient.CreateMemberProfileCommand(
                    account.getId(), command.email(), verifiedIdentity.name(), command.nickname(),
                    verifiedIdentity.phoneNumber(), command.agreedTerms(), command.address()));
        } catch (DependencyFailureException e) {
            // 보상 트랜잭션: 방금 커밋한 계정을 삭제하고 원래 예외(503 DEPENDENCY_FAILURE)를 그대로 전파
            accountRepository.deleteById(account.getId());
            throw e;
        }

        var tokens = tokenIssuer.issue(account.getId(), account.getRole());
        return new SignupResult(account.getId(), memberProfile.memberId(), tokens.accessToken(), tokens.refreshToken());
    }

    public record SignupCommand(
            String email,
            String password,
            String verificationToken,
            String name,
            String nickname,
            String phoneNumber,
            List<String> agreedTerms,
            Map<String, Object> address
    ) {
    }

    public record SignupResult(UUID accountId, UUID memberId, String accessToken, String refreshToken) {
    }
}
