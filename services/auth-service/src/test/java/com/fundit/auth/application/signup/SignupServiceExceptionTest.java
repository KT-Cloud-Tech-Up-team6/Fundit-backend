package com.fundit.auth.application.signup;

import com.fundit.auth.application.identity.IdentityVerificationStore;
import com.fundit.auth.domain.AuthErrorCode;
import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.DependencyFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignupServiceExceptionTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private MemberServiceClient memberServiceClient;
    @Mock
    private IdentityVerificationStore identityVerificationStore;

    @InjectMocks
    private SignupService signupService;

    @Test
    void 이메일이_이미_존재하면_계정을_생성하지_않고_예외가_발생한다() {
        // given
        when(accountRepository.existsByEmail("dup@fundit.com")).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> signupService.signup(new SignupService.SignupCommand(
                "dup@fundit.com", "pw", "verify-token", "홍길동", "01012345678", List.of("TOS"), Map.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void 본인인증_토큰이_만료됐거나_존재하지_않으면_예외가_발생한다() {
        // given
        when(accountRepository.existsByEmail(any())).thenReturn(false);
        when(identityVerificationStore.consume("expired-token")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> signupService.signup(new SignupService.SignupCommand(
                "test@fundit.com", "pw", "expired-token", "홍길동", "01012345678", List.of("TOS"), Map.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.TOKEN_INVALID);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void 본인인증된_휴대폰번호와_요청_휴대폰번호가_다르면_예외가_발생한다() {
        // given
        when(accountRepository.existsByEmail(any())).thenReturn(false);
        when(identityVerificationStore.consume("verify-token")).thenReturn(Optional.of(
                new IdentityVerificationStore.VerifiedIdentity("홍길동", "01099998888", null)));

        // when & then
        assertThatThrownBy(() -> signupService.signup(new SignupService.SignupCommand(
                "test@fundit.com", "pw", "verify-token", "홍길동", "01012345678", List.of("TOS"), Map.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.TOKEN_INVALID);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void member_service_호출이_실패하면_계정을_삭제하고_예외를_그대로_전파한다() {
        // given
        when(accountRepository.existsByEmail(any())).thenReturn(false);
        when(identityVerificationStore.consume("verify-token")).thenReturn(Optional.of(
                new IdentityVerificationStore.VerifiedIdentity("홍길동", "01012345678", null)));
        when(passwordEncoder.encode(any())).thenReturn("hashed-pw");
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        DependencyFailureException dependencyFailure = new DependencyFailureException(new RuntimeException("connect refused"));
        when(memberServiceClient.createProfile(any())).thenThrow(dependencyFailure);

        // when & then
        assertThatThrownBy(() -> signupService.signup(new SignupService.SignupCommand(
                "test@fundit.com", "pw", "verify-token", "홍길동", "01012345678", List.of("TOS"), Map.of())))
                .isInstanceOf(DependencyFailureException.class)
                .extracting(e -> ((DependencyFailureException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.DEPENDENCY_FAILURE);
        verify(accountRepository).deleteById(any());
    }
}
