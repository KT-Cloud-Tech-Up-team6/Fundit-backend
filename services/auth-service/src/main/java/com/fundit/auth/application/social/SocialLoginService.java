package com.fundit.auth.application.social;

import com.fundit.auth.application.token.TokenIssuer;
import com.fundit.auth.domain.AuthErrorCode;
import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.auth.domain.account.SocialProvider;
import com.fundit.common.error.BusinessException;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
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
    private final SocialSignupTokenStore signupTokenStore;
    private final TokenIssuer tokenIssuer;
    private final Duration signupTokenTtl;

    public SocialLoginService(
            List<SocialProviderClient> providerClients,
            AccountRepository accountRepository,
            EmailConflictChecker emailConflictChecker,
            SocialSignupTokenStore signupTokenStore,
            TokenIssuer tokenIssuer,
            @Value("${oauth.signup-token-ttl}") Duration signupTokenTtl) {
        providerClients.forEach(client -> this.clients.put(client.provider(), client));
        this.accountRepository = accountRepository;
        this.emailConflictChecker = emailConflictChecker;
        this.signupTokenStore = signupTokenStore;
        this.tokenIssuer = tokenIssuer;
        this.signupTokenTtl = signupTokenTtl;
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
            var tokens = tokenIssuer.issue(linked.getId(), linked.getRole());
            return SocialLoginResult.loggedIn(
                    tokens.accessToken(), tokens.refreshToken(), linked.isMustChangePassword());
        }

        // 이메일이 없으면(카카오 미동의) 여기서는 판정할 수 없다 — 가입 화면에서 이메일을 받은 뒤
        // SocialSignupService가 같은 판정을 다시 돌린다.
        Account localAccount = emailConflictChecker.checkOrThrow(identity.email());
        if (localAccount != null) {
            // 정책 A(본인인증 후 연동)는 아직 없다. 조용히 새 계정을 만들지 않고 막는다 —
            // uq_accounts_email이 UNIQUE라 어차피 가입 단계에서 실패한다.
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        String signupToken = UuidCreator.getTimeOrderedEpoch().toString();
        signupTokenStore.save(signupToken, new SocialSignupTokenStore.PendingSocialSignup(
                provider, identity.socialId(), identity.email(), identity.name()), signupTokenTtl);
        return SocialLoginResult.needsSignup(
                provider, signupToken, identity.email(), identity.name());
    }

    /**
     * 가입이 필요한 경우 {@code accessToken}은 null이고, 로그인된 경우 {@code signupToken}이 null이다.
     * 응답 DTO는 presentation에서 이 값을 보고 두 형태 중 하나로 만든다.
     */
    public record SocialLoginResult(
            boolean needsSignup,
            String accessToken,
            String refreshToken,
            boolean mustChangePassword,
            SocialProvider provider,
            String signupToken,
            String email,
            String name) {

        static SocialLoginResult loggedIn(String accessToken, String refreshToken, boolean mustChangePassword) {
            return new SocialLoginResult(false, accessToken, refreshToken, mustChangePassword, null, null, null, null);
        }

        static SocialLoginResult needsSignup(
                SocialProvider provider, String signupToken, String email, String name) {
            return new SocialLoginResult(true, null, null, false, provider, signupToken, email, name);
        }
    }
}
