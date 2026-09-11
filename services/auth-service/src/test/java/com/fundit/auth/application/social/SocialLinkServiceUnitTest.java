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

/** 정책 A 연동의 정상 흐름. 예외 케이스는 SocialLinkServiceUnitExceptionTest에 있다. */
@ExtendWith(MockitoExtension.class)
class SocialLinkServiceUnitTest {

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
        when(socialTokenStore.consumeLink("link-token")).thenReturn(Optional.of(
                new SocialTokenStore.PendingSocialLink(SocialProvider.KAKAO, "kakao-1", ACCOUNT_ID)));
        when(identityVerificationStore.consume("verify-token")).thenReturn(Optional.of(
                new IdentityVerificationStore.VerifiedIdentity("홍길동", "01012345678", LocalDate.of(1990, 1, 1))));
    }

    private Account localAccount() {
        return Account.builder()
                .id(ACCOUNT_ID).email("user@fundit.com").passwordHash("hashed")
                .role(Role.MEMBER).failedLoginCount(0).mustChangePassword(false)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
    }
}
