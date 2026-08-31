package com.fundit.auth.application.signup;

import com.fundit.auth.application.token.TokenIssuer;
import com.fundit.auth.domain.AuthErrorCode;
import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.auth.domain.account.Role;
import com.fundit.common.error.BusinessException;
import com.fundit.common.error.DependencyFailureException;
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
    private final TokenIssuer tokenIssuer;

    /**
     * verificationToken(휴대폰 본인인증 임시토큰)은 이번 슬라이스에서 검증하지 않는다(사용자 확정) —
     * AUTH-004/005가 아직 구현되지 않아서다. SignupCommand에도 이 필드를 두지 않는다.
     * ponytail: 본인인증 미검증 — AUTH-004/005 구현 후 verificationToken→phoneNumber 대조 로직 추가 필요.
     */
    public SignupResult signup(SignupCommand command) {
        if (accountRepository.existsByEmail(command.email())) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        Instant now = Instant.now();
        Account account = Account.builder()
                .id(UUID.randomUUID())
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
                    account.getId(), command.email(), command.name(), command.phoneNumber(),
                    command.agreedTerms(), command.address()));
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
            String name,
            String phoneNumber,
            List<String> agreedTerms,
            Map<String, Object> address
    ) {
    }

    public record SignupResult(UUID accountId, UUID memberId, String accessToken, String refreshToken) {
    }
}
