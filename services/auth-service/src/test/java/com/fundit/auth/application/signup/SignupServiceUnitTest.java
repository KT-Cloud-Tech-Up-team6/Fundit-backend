package com.fundit.auth.application.signup;

import com.fundit.auth.application.identity.IdentityVerificationStore;
import com.fundit.auth.application.token.TokenIssuer;
import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.auth.domain.account.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignupServiceUnitTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private MemberServiceClient memberServiceClient;
    @Mock
    private IdentityVerificationStore identityVerificationStore;
    @Mock
    private TokenIssuer tokenIssuer;

    @InjectMocks
    private SignupService signupService;

    @Test
    void 정상_가입이면_계정과_프로필을_생성하고_토큰을_발급한다() {
        // given
        UUID memberId = UUID.randomUUID();
        when(accountRepository.existsByEmail("test@fundit.com")).thenReturn(false);
        when(identityVerificationStore.consume("verify-token")).thenReturn(Optional.of(
                new IdentityVerificationStore.VerifiedIdentity("홍길동", "01012345678", null)));
        when(passwordEncoder.encode("pw")).thenReturn("hashed-pw");
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(memberServiceClient.createProfile(any())).thenReturn(new MemberServiceClient.MemberProfile(memberId));
        when(tokenIssuer.issue(any(), org.mockito.ArgumentMatchers.eq(Role.MEMBER)))
                .thenReturn(new TokenIssuer.IssuedTokens("access-token", "refresh-token"));

        // when
        SignupService.SignupResult result = signupService.signup(new SignupService.SignupCommand(
                "test@fundit.com", "pw", "verify-token", "홍길동", "01012345678", List.of("TOS"), Map.of()));

        // then
        assertThat(result.memberId()).isEqualTo(memberId);
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
    }
}
