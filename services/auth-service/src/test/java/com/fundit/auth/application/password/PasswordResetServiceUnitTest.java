package com.fundit.auth.application.password;

import com.fundit.auth.application.mail.MailSender;
import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.auth.domain.account.Role;
import com.fundit.auth.infrastructure.persistence.passwordresettoken.PasswordResetTokenJpaEntity;
import com.fundit.auth.infrastructure.persistence.passwordresettoken.PasswordResetTokenJpaRepository;
import com.fundit.auth.infrastructure.persistence.refreshtoken.RefreshTokenJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceUnitTest {

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

    private Account account(UUID id, String email) {
        return Account.builder().id(id).email(email).passwordHash("old").role(Role.MEMBER).build();
    }

    @Test
    void 세_값이_모두_맞으면_재설정_링크를_보낸다() {
        // given
        UUID accountId = UUID.randomUUID();
        given(accountRepository.findByNameAndPhone("김펀딧", "01012345678"))
                .willReturn(Optional.of(account(accountId, "user@fundit.com")));

        // when
        service().requestReset("김펀딧", "01012345678", "user@fundit.com");

        // then — 토큰을 저장하고 그 토큰이 링크에 실린다
        ArgumentCaptor<PasswordResetTokenJpaEntity> saved =
                ArgumentCaptor.forClass(PasswordResetTokenJpaEntity.class);
        verify(resetTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getAccountId()).isEqualTo(accountId);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailSender).send(org.mockito.ArgumentMatchers.eq("user@fundit.com"), anyString(), body.capture());
        assertThat(body.getValue()).contains(saved.getValue().getTokenId().toString());
    }

    @Test
    void 재발급하면_이전_링크를_무효화한다() {
        // given — 메일함에 쌓인 옛 링크가 계속 살아있으면 안 된다
        UUID accountId = UUID.randomUUID();
        given(accountRepository.findByNameAndPhone("김펀딧", "01012345678"))
                .willReturn(Optional.of(account(accountId, "user@fundit.com")));

        // when
        service().requestReset("김펀딧", "01012345678", "user@fundit.com");

        // then
        verify(resetTokenRepository).deleteAllByAccountId(accountId);
    }

    @Test
    void 이메일_대소문자가_달라도_같은_계정으로_본다() {
        // given
        given(accountRepository.findByNameAndPhone("김펀딧", "01012345678"))
                .willReturn(Optional.of(account(UUID.randomUUID(), "User@Fundit.com")));

        // when
        service().requestReset("김펀딧", "01012345678", "user@fundit.com");

        // then
        verify(mailSender).send(anyString(), anyString(), anyString());
    }

    @Test
    void 비밀번호를_바꾸면_기존_세션을_전부_끊는다() {
        // given — 바꾸는 이유가 탈취 의심인데 기존 세션이 살아 있으면 바꾼 의미가 없다
        UUID tokenId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        given(resetTokenRepository.deleteAndReturnAccountId(tokenId)).willReturn(Optional.of(accountId));
        given(accountRepository.findById(accountId)).willReturn(Optional.of(account(accountId, "user@fundit.com")));
        given(passwordEncoder.encode("NewPassw0rd!")).willReturn("new-hash");

        // when
        service().confirmReset(tokenId.toString(), "NewPassw0rd!");

        // then
        verify(accountRepository).save(any());
        verify(refreshTokenRepository).deleteAllByAccountId(accountId);
    }

    @Test
    void 계정이_없으면_조용히_아무것도_하지_않는다() {
        // given — 여기서 404를 주면 이메일을 넣어보며 가입 여부를 캐낼 수 있다
        given(accountRepository.findByNameAndPhone("없는사람", "01000000000")).willReturn(Optional.empty());

        // when
        service().requestReset("없는사람", "01000000000", "none@fundit.com");

        // then
        verify(resetTokenRepository, never()).save(any());
        verify(mailSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void 이메일만_다르면_보내지_않는다() {
        // given — 이름·전화번호가 맞아도 세 번째 값이 틀리면 본인이 아니다
        given(accountRepository.findByNameAndPhone("김펀딧", "01012345678"))
                .willReturn(Optional.of(account(UUID.randomUUID(), "user@fundit.com")));

        // when
        service().requestReset("김펀딧", "01012345678", "other@fundit.com");

        // then
        verify(mailSender, never()).send(anyString(), anyString(), anyString());
    }
}
