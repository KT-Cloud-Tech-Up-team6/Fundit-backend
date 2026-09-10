package com.fundit.auth.application.social;

import com.fundit.auth.application.token.TokenIssuer;
import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountLockedException;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.auth.domain.account.SocialProvider;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 소셜 로그인(AUTH-002). 인가 코드로 제공자 신원을 확인한 뒤 세 갈래로 나뉜다.
 *
 * <ol>
 *   <li>연동된 계정이 있으면 그대로 로그인시킨다.</li>
 *   <li>같은 이메일이 이미 쓰이고 있으면 {@link EmailConflictChecker}의 판정을 따른다
 *       (정책 B·C는 409, 정책 A는 아직 연동 흐름이 없어 "이미 가입된 이메일"로 막는다).</li>
 *   <li>둘 다 아니면 가입이 필요하다 — 404가 아니라 200 + signupToken으로 응답한다.
 *       인가 코드는 1회용이라 프론트가 가입 화면으로 넘어간 시점엔 이미 소진돼 있다.</li>
 * </ol>
 */
@Service
public class SocialLoginService {

    private final Map<SocialProvider, SocialProviderClient> clients = new EnumMap<>(SocialProvider.class);
    private final AccountRepository accountRepository;
    private final EmailConflictChecker emailConflictChecker;
    private final SocialTokenStore socialTokenStore;
    private final TokenIssuer tokenIssuer;
    private final Duration pendingTokenTtl;

    public SocialLoginService(
            List<SocialProviderClient> providerClients,
            AccountRepository accountRepository,
            EmailConflictChecker emailConflictChecker,
            SocialTokenStore socialTokenStore,
            TokenIssuer tokenIssuer,
            @Value("${oauth.pending-token-ttl}") Duration pendingTokenTtl) {
        providerClients.forEach(client -> this.clients.put(client.provider(), client));
        this.accountRepository = accountRepository;
        this.emailConflictChecker = emailConflictChecker;
        this.socialTokenStore = socialTokenStore;
        this.tokenIssuer = tokenIssuer;
        this.pendingTokenTtl = pendingTokenTtl;
    }

    public SocialLoginResult login(SocialProvider provider, String authorizationCode) {
        SocialProviderClient client = clients.get(provider);
        if (client == null) {
            // enum 값인데 구현체가 없으면 배선이 빠진 것이다 — 사용자 입력 문제가 아니다.
            throw new IllegalStateException("연동 구현이 없는 제공자: " + provider);
        }

        SocialProviderClient.SocialIdentity identity = client.fetchIdentity(authorizationCode);

        Account linked = accountRepository.findBySocial(provider, identity.socialId()).orElse(null);
        if (linked != null) {
            // 일반 로그인(LoginService)이 막는 잠금을 여기서도 막는다 — locked_until은 계정 상태이지
            // 비밀번호 방식만의 상태가 아니다. 안 막으면 소셜이 연동된 계정은 잠금이 무의미해진다.
            if (linked.isLocked(Instant.now())) {
                throw new AccountLockedException(linked.getLockedUntil());
            }
            var tokens = tokenIssuer.issue(linked.getId(), linked.getRole());
            return SocialLoginResult.loggedIn(
                    tokens.accessToken(), tokens.refreshToken(), linked.isMustChangePassword());
        }

        // 이메일이 없으면(카카오 미동의) 여기서는 판정할 수 없다 — 가입 화면에서 이메일을 받은 뒤
        // SocialSignupService가 같은 판정을 다시 돌린다.
        Account localAccount = emailConflictChecker.checkOrThrow(identity.email());
        if (localAccount != null) {
            // 정책 A — 자체가입 계정이 있다. 본인인증을 거치면 연동할 수 있다.
            // 연동 대상 계정은 서버가 여기서 정해 토큰에 담는다(클라이언트가 지목하지 못하게).
            String linkToken = UuidCreator.getTimeOrderedEpoch().toString();
            socialTokenStore.saveLink(linkToken, new SocialTokenStore.PendingSocialLink(
                    provider, identity.socialId(), localAccount.getId()), pendingTokenTtl);
            return SocialLoginResult.needsLink(provider, linkToken);
        }

        String signupToken = UuidCreator.getTimeOrderedEpoch().toString();
        socialTokenStore.saveSignup(signupToken, new SocialTokenStore.PendingSocialSignup(
                provider, identity.socialId(), identity.email(), identity.name()), pendingTokenTtl);
        return SocialLoginResult.needsSignup(
                provider, signupToken, identity.email(), identity.name());
    }

    /**
     * 가입이 필요한 경우 {@code accessToken}은 null이고, 로그인된 경우 {@code signupToken}이 null이다.
     * 응답 DTO는 presentation에서 이 값을 보고 두 형태 중 하나로 만든다.
     */
    public record SocialLoginResult(
            boolean needsSignup,
            boolean needsLink,
            String linkToken,
            String accessToken,
            String refreshToken,
            boolean mustChangePassword,
            SocialProvider provider,
            String signupToken,
            String email,
            String name) {

        static SocialLoginResult loggedIn(String accessToken, String refreshToken, boolean mustChangePassword) {
            return new SocialLoginResult(
                    false, false, null, accessToken, refreshToken, mustChangePassword, null, null, null, null);
        }

        static SocialLoginResult needsSignup(
                SocialProvider provider, String signupToken, String email, String name) {
            return new SocialLoginResult(
                    true, false, null, null, null, false, provider, signupToken, email, name);
        }

        /** 정책 A — 응답에 이메일을 담지 않는다. 소셜 로그인 시도만으로 타인의 가입 이메일이 드러나면 안 된다. */
        static SocialLoginResult needsLink(SocialProvider provider, String linkToken) {
            return new SocialLoginResult(
                    false, true, linkToken, null, null, false, provider, null, null, null);
        }
    }
}
