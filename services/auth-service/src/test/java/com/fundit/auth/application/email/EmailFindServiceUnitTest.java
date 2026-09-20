package com.fundit.auth.application.email;

import com.fundit.auth.application.identity.IdentityVerificationStore;
import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.auth.domain.account.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailFindServiceUnitTest {

    @Mock private AccountRepository accountRepository;
    @Mock private IdentityVerificationStore identityVerificationStore;
    @Mock private FindEmailAttemptLimiter attemptLimiter;

    @InjectMocks private EmailFindService emailFindService;

    private Account account(String email) {
        return Account.builder().id(UUID.randomUUID()).email(email).role(Role.MEMBER).build();
    }

    @Test
    void 이름과_전화번호가_맞으면_마스킹된_이메일을_돌려준다() {
        // given
        given(accountRepository.findByNameAndPhone("김펀딧", "01012345678"))
                .willReturn(Optional.of(account("1234qwer@gmail.com")));

        // when
        String masked = emailFindService.findMasked("김펀딧", "01012345678");

        // then
        assertThat(masked).isEqualTo("1234q***@gmail.com");
    }

    @Test
    void 가입_계정이_없으면_null이다() {
        // given — 계정 유무와 무관하게 응답 형태는 같아야 한다(AUTH-009)
        given(accountRepository.findByNameAndPhone("없는사람", "01000000000"))
                .willReturn(Optional.empty());

        // when
        String masked = emailFindService.findMasked("없는사람", "01000000000");

        // then
        assertThat(masked).isNull();
    }

    @Test
    void 전문_공개는_본문이_아니라_본인인증_결과로_조회한다() {
        // given — 본문으로 이름·전화번호를 받으면 1단계에서 알아낸 남의 번호에
        // 자기 인증 토큰을 붙여 전문을 꺼낼 수 있다
        given(identityVerificationStore.consume("verified-token")).willReturn(Optional.of(
                new IdentityVerificationStore.VerifiedIdentity("김펀딧", "01012345678", LocalDate.of(1995, 3, 1))));
        given(accountRepository.findByNameAndPhone("김펀딧", "01012345678"))
                .willReturn(Optional.of(account("1234qwer@gmail.com")));

        // when
        String email = emailFindService.reveal("verified-token");

        // then
        assertThat(email).isEqualTo("1234qwer@gmail.com");
        verify(attemptLimiter, never()).exceeded(org.mockito.ArgumentMatchers.anyString());
    }
}
