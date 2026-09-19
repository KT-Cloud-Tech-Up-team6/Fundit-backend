package com.fundit.auth.application.password;

import com.fundit.auth.application.mail.MailSender;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.auth.infrastructure.persistence.passwordresettoken.PasswordResetTokenJpaRepository;
import com.fundit.auth.infrastructure.persistence.refreshtoken.RefreshTokenJpaRepository;
import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceUnitExceptionTest {

    @Mock private AccountRepository accountRepository;
    @Mock private PasswordResetTokenJpaRepository resetTokenRepository;
    @Mock private RefreshTokenJpaRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private MailSender mailSender;

    private PasswordResetService service() {
        return new PasswordResetService(accountRepository, resetTokenRepository, refreshTokenRepository,
                passwordEncoder, mailSender, Duration.ofMinutes(30),
                "https://fundit.com/reset-password?token=%s");
    }

    @Test
    void 이미_사용한_토큰은_401이다() {
        // given — 확인과 폐기가 한 문장이라 두 번째 호출은 빈 결과가 온다
        UUID tokenId = UUID.randomUUID();
        given(resetTokenRepository.deleteAndReturnAccountId(tokenId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service().confirmReset(tokenId.toString(), "NewPassw0rd!"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.TOKEN_INVALID);
        verify(accountRepository, never()).save(any());
        verify(refreshTokenRepository, never()).deleteAllByAccountId(any());
    }

    @Test
    void 형식이_깨진_토큰도_없는_토큰과_같은_401이다() {
        // given & when & then — 형식 오류를 400으로 구분하면 토큰 생김새를 알려주는 셈이다
        assertThatThrownBy(() -> service().confirmReset("not-a-uuid", "NewPassw0rd!"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.TOKEN_INVALID);
        verify(resetTokenRepository, never()).deleteAndReturnAccountId(any());
    }
}
