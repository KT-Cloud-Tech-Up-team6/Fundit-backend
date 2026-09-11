package com.fundit.auth.application.social;

import com.fundit.auth.application.token.TokenIssuer;
import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.auth.domain.account.Role;
import com.fundit.auth.domain.account.SocialProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialLoginServiceUnitTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private SocialTokenStore socialTokenStore;
    @Mock
    private TokenIssuer tokenIssuer;
    @Mock
    private SocialProviderClient kakaoClient;

    private SocialLoginService service() {
        when(kakaoClient.provider()).thenReturn(SocialProvider.KAKAO);
        return new SocialLoginService(
                List.of(kakaoClient), accountRepository,
                new EmailConflictChecker(accountRepository), socialTokenStore, tokenIssuer, Duration.ofMinutes(10));
    }

    @Test
    void 연동된_계정이_있으면_바로_로그인시킨다() {
        // given
        when(kakaoClient.fetchIdentity("code"))
                .thenReturn(new SocialProviderClient.SocialIdentity("kakao-1", "user@kakao.com", "응원왕"));
        Account account = account("user@kakao.com", "KAKAO", "kakao-1");
        when(accountRepository.findBySocial(SocialProvider.KAKAO, "kakao-1")).thenReturn(Optional.of(account));
        when(tokenIssuer.issue(account.getId(), Role.MEMBER))
                .thenReturn(new TokenIssuer.IssuedTokens("access", "refresh"));

        // when
        var result = service().login(SocialProvider.KAKAO, "code");

        // then
        assertThat(result.needsSignup()).isFalse();
        assertThat(result.accessToken()).isEqualTo("access");
        verify(socialTokenStore, never()).saveSignup(anyString(), any(), any());
    }

    @Test
    void 연동_계정이_없으면_에러가_아니라_가입토큰을_준다() {
        // given — 인가 코드는 1회용이라 가입 화면에서 다시 못 쓴다. 검증된 신원을 서버가 들고 있는다
        when(kakaoClient.fetchIdentity("code"))
                .thenReturn(new SocialProviderClient.SocialIdentity("kakao-1", "new@kakao.com", "응원왕"));
        when(accountRepository.findBySocial(SocialProvider.KAKAO, "kakao-1")).thenReturn(Optional.empty());
        when(accountRepository.findByEmail("new@kakao.com")).thenReturn(Optional.empty());

        // when
        var result = service().login(SocialProvider.KAKAO, "code");

        // then
        assertThat(result.needsSignup()).isTrue();
        assertThat(result.signupToken()).isNotBlank();
        verify(socialTokenStore).saveSignup(eq(result.signupToken()), any(), eq(Duration.ofMinutes(10)));
    }

    @Test
    void 제공자가_이메일을_안_주면_충돌_조회_없이_가입_단계로_넘긴다() {
        // given — 카카오 이메일 미동의. 이메일이 없으면 판정 자체가 불가능하다
        when(kakaoClient.fetchIdentity("code"))
                .thenReturn(new SocialProviderClient.SocialIdentity("kakao-1", null, null));
        when(accountRepository.findBySocial(SocialProvider.KAKAO, "kakao-1")).thenReturn(Optional.empty());

        // when
        var result = service().login(SocialProvider.KAKAO, "code");

        // then
        assertThat(result.needsSignup()).isTrue();
        assertThat(result.email()).isNull();
        verify(accountRepository, never()).findByEmail(anyString());
    }

    private Account account(String email, String socialProvider, String socialId) {
        return Account.builder()
                .id(UUID.randomUUID()).email(email)
                .socialProvider(socialProvider).socialId(socialId)
                .role(Role.MEMBER).failedLoginCount(0).mustChangePassword(false)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
    }

    @Test
    void 자체가입_계정과_이메일이_같으면_연동_안내를_준다() {
        // given — 정책 A. 여기서 새 계정을 만들면 uq_accounts_email에 걸린다
        when(kakaoClient.fetchIdentity("code"))
                .thenReturn(new SocialProviderClient.SocialIdentity("kakao-1", "user@fundit.com", "응원왕"));
        when(accountRepository.findBySocial(SocialProvider.KAKAO, "kakao-1")).thenReturn(Optional.empty());
        Account local = account("user@fundit.com", null, null);
        when(accountRepository.findByEmail("user@fundit.com")).thenReturn(Optional.of(local));

        // when
        var result = service().login(SocialProvider.KAKAO, "code");

        // then
        assertThat(result.needsLink()).isTrue();
        assertThat(result.linkToken()).isNotBlank();
        // 소셜 로그인 시도만으로 타인의 가입 이메일이 드러나면 안 된다
        assertThat(result.email()).isNull();
        // 연동 대상 계정은 서버가 토큰에 담는다 — 클라이언트가 지목하지 못하게
        verify(socialTokenStore).saveLink(eq(result.linkToken()),
                eq(new SocialTokenStore.PendingSocialLink(SocialProvider.KAKAO, "kakao-1", local.getId())),
                eq(Duration.ofMinutes(10)));
    }

    @Test
    void 잠긴_계정은_소셜_로그인으로도_들어올_수_없다() {
        // given — locked_until은 계정 상태다. 소셜만 통과시키면 잠금이 무의미해진다
        when(kakaoClient.fetchIdentity("code"))
                .thenReturn(new SocialProviderClient.SocialIdentity("kakao-1", "user@kakao.com", "응원왕"));
        Account locked = Account.builder()
                .id(UUID.randomUUID()).email("user@kakao.com")
                .socialProvider("KAKAO").socialId("kakao-1")
                .role(Role.MEMBER).failedLoginCount(0).mustChangePassword(false)
                .lockedUntil(Instant.now().plusSeconds(600))
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        when(accountRepository.findBySocial(SocialProvider.KAKAO, "kakao-1")).thenReturn(Optional.of(locked));

        // when & then
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service().login(SocialProvider.KAKAO, "code"))
                .isInstanceOf(com.fundit.auth.domain.account.AccountLockedException.class);
        verify(tokenIssuer, never()).issue(any(), any());
    }
}
