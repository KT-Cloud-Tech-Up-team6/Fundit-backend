package com.fundit.auth.application.social;

import com.fundit.auth.application.identity.IdentityVerificationStore;
import com.fundit.auth.application.signup.MemberServiceClient;
import com.fundit.auth.application.token.TokenIssuer;
import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.auth.domain.account.Role;
import com.fundit.auth.domain.account.SocialProvider;
import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 정책 A의 본인 확인. <b>여기가 뚫리면 피해자의 이메일만 알면 계정을 탈취할 수 있다</b> —
 * 그래서 실패 케이스를 정상 케이스보다 촘촘히 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class SocialLinkServiceUnitExceptionTest {

    private static final UUID ACCOUNT_ID = UUID.randomUUID();

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private SocialTokenStore socialTokenStore;
    @Mock
    private IdentityVerificationStore identityVerificationStore;
    @Mock
    private MemberServiceClient memberServiceClient;
    @Mock
    private TokenIssuer tokenIssuer;

    @InjectMocks
    private SocialLinkService service;

    @Test
    void 본인인증_휴대폰이_계정_주인의_것이_아니면_연동하지_않는다() {
        // given — 핵심 방어선. 남의 이메일로 소셜 로그인한 뒤 자기 본인인증을 들고 온 경우
        givenValidTokens();
        when(memberServiceClient.phoneMatches(ACCOUNT_ID, "01012345678")).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> service.link("link-token", "verify-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.FORBIDDEN);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void 연동토큰이_만료됐거나_이미_쓰였으면_401이다() {
        // given — 1회 소비. 같은 토큰으로 두 번 연동할 수 없다
        when(socialTokenStore.consumeLink("link-token")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.link("link-token", "verify-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.TOKEN_EXPIRED);
        verify(memberServiceClient, never()).phoneMatches(any(), any());
    }

    @Test
    void 본인인증_토큰이_없으면_확인_요청도_보내지_않는다() {
        // given
        when(socialTokenStore.consumeLink("link-token")).thenReturn(Optional.of(pendingLink()));
        when(identityVerificationStore.consume("verify-token")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.link("link-token", "verify-token"))
                .isInstanceOf(BusinessException.class);
        verify(memberServiceClient, never()).phoneMatches(any(), any());
    }

    @Test
    void 본인_확인을_통과하면_소셜을_붙이고_비밀번호는_그대로_둔다() {
        // given — 연동 후에도 일반 로그인과 소셜 로그인이 둘 다 되어야 한다
        givenValidTokens();
        when(memberServiceClient.phoneMatches(ACCOUNT_ID, "01012345678")).thenReturn(true);
        Account account = localAccount();
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(tokenIssuer.issue(ACCOUNT_ID, Role.MEMBER))
                .thenReturn(new TokenIssuer.IssuedTokens("access", "refresh"));

        // when
        var tokens = service.link("link-token", "verify-token");

        // then
        assertThat(tokens.accessToken()).isEqualTo("access");
        assertThat(account.getSocialProvider()).isEqualTo("KAKAO");
        assertThat(account.getSocialId()).isEqualTo("kakao-1");
        assertThat(account.getPasswordHash()).isEqualTo("hashed");
        verify(accountRepository).save(account);
    }

    private void givenValidTokens() {
        when(socialTokenStore.consumeLink("link-token")).thenReturn(Optional.of(pendingLink()));
        when(identityVerificationStore.consume("verify-token")).thenReturn(Optional.of(
                new IdentityVerificationStore.VerifiedIdentity("홍길동", "01012345678", LocalDate.of(1990, 1, 1))));
    }

    private SocialTokenStore.PendingSocialLink pendingLink() {
        return new SocialTokenStore.PendingSocialLink(SocialProvider.KAKAO, "kakao-1", ACCOUNT_ID);
    }

    private Account localAccount() {
        return Account.builder()
                .id(ACCOUNT_ID).email("user@fundit.com").passwordHash("hashed")
                .role(Role.MEMBER).failedLoginCount(0).mustChangePassword(false)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
    }
}
