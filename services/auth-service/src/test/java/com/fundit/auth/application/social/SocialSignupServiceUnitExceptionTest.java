package com.fundit.auth.application.social;

import com.fundit.auth.application.identity.IdentityVerificationStore;
import com.fundit.auth.application.signup.MemberServiceClient;
import com.fundit.auth.application.token.TokenIssuer;
import com.fundit.auth.domain.AuthErrorCode;
import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.auth.domain.account.Role;
import com.fundit.auth.domain.account.SocialProvider;
import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.DependencyFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialSignupServiceUnitExceptionTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private SocialSignupTokenStore signupTokenStore;
    @Mock
    private IdentityVerificationStore identityVerificationStore;
    @Mock
    private MemberServiceClient memberServiceClient;
    @Mock
    private TokenIssuer tokenIssuer;

    private SocialSignupService service() {
        return new SocialSignupService(accountRepository, new EmailConflictChecker(accountRepository),
                signupTokenStore, identityVerificationStore, memberServiceClient, tokenIssuer);
    }

    @Test
    void 가입토큰이_만료됐거나_이미_쓰였으면_401이다() {
        // given — 1회 소비라 두 번째 호출은 항상 비어있다
        when(signupTokenStore.consume("token")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service().signup(command("token", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.TOKEN_EXPIRED);
    }

    @Test
    void 그_사이_같은_소셜로_가입이_끝났으면_409로_막는다() {
        // given — 다른 탭에서 먼저 가입이 끝난 경우. 여기서 계속 진행하면 uq_accounts_social 위반이다
        givenValidTokens();
        when(accountRepository.findBySocial(SocialProvider.KAKAO, "kakao-1"))
                .thenReturn(Optional.of(account()));

        // when & then
        assertThatThrownBy(() -> service().signup(command("token", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.SOCIAL_ACCOUNT_EXISTS);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void 제공자도_사용자도_이메일을_주지_않으면_400이다() {
        // given — accounts.email이 NOT NULL이라 이메일 없이는 계정을 만들 수 없다
        when(signupTokenStore.consume("token")).thenReturn(Optional.of(
                new SocialSignupTokenStore.PendingSocialSignup(SocialProvider.KAKAO, "kakao-1", null, null)));
        when(identityVerificationStore.consume("verify")).thenReturn(Optional.of(
                new IdentityVerificationStore.VerifiedIdentity("홍길동", "01012345678", LocalDate.of(1990, 1, 1))));
        when(accountRepository.findBySocial(SocialProvider.KAKAO, "kakao-1")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service().signup(command("token", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_INPUT);
    }

    @Test
    void member_생성이_실패하면_방금_만든_계정을_지운다() {
        // given — 프로필 없는 고아 계정을 남기지 않는다(보상 트랜잭션, SignupService와 동일)
        givenValidTokens();
        when(accountRepository.findBySocial(SocialProvider.KAKAO, "kakao-1")).thenReturn(Optional.empty());
        when(accountRepository.findByEmail("user@kakao.com")).thenReturn(Optional.empty());
        Account saved = account();
        when(accountRepository.save(any())).thenReturn(saved);
        when(memberServiceClient.createProfile(any()))
                .thenThrow(new DependencyFailureException(new IllegalStateException("member 다운")));

        // when & then
        assertThatThrownBy(() -> service().signup(command("token", null)))
                .isInstanceOf(DependencyFailureException.class);
        verify(accountRepository).deleteById(saved.getId());
    }

    @Test
    void 소셜_전용_계정은_비밀번호_없이_만들어진다() {
        // given — V1__init_schema.sql이 password_hash를 NULL 허용으로 둔 이유다
        givenValidTokens();
        when(accountRepository.findBySocial(SocialProvider.KAKAO, "kakao-1")).thenReturn(Optional.empty());
        when(accountRepository.findByEmail("user@kakao.com")).thenReturn(Optional.empty());
        Account saved = account();
        when(accountRepository.save(any())).thenReturn(saved);
        when(memberServiceClient.createProfile(any()))
                .thenReturn(new MemberServiceClient.MemberProfile(UUID.randomUUID()));
        when(tokenIssuer.issue(any(), any())).thenReturn(new TokenIssuer.IssuedTokens("access", "refresh"));

        // when
        service().signup(command("token", null));

        // then
        verify(accountRepository).save(org.mockito.ArgumentMatchers.argThat(a -> {
            assertThat(a.getPasswordHash()).isNull();
            assertThat(a.getSocialProvider()).isEqualTo("KAKAO");
            assertThat(a.getSocialId()).isEqualTo("kakao-1");
            return true;
        }));
    }

    private void givenValidTokens() {
        when(signupTokenStore.consume("token")).thenReturn(Optional.of(
                new SocialSignupTokenStore.PendingSocialSignup(
                        SocialProvider.KAKAO, "kakao-1", "user@kakao.com", "응원왕")));
        when(identityVerificationStore.consume("verify")).thenReturn(Optional.of(
                new IdentityVerificationStore.VerifiedIdentity("홍길동", "01012345678", LocalDate.of(1990, 1, 1))));
    }

    private SocialSignupService.SocialSignupCommand command(String signupToken, String email) {
        return new SocialSignupService.SocialSignupCommand(
                signupToken, "verify", email, List.of("SERVICE_USE", "PRIVACY", "AGE_OVER_14"), null);
    }

    private Account account() {
        return Account.builder()
                .id(UUID.randomUUID()).email("user@kakao.com")
                .socialProvider("KAKAO").socialId("kakao-1")
                .role(Role.MEMBER).failedLoginCount(0).mustChangePassword(false)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
    }
}
