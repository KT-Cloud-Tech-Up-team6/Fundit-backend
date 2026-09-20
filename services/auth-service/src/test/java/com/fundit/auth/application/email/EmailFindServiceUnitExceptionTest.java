package com.fundit.auth.application.email;

import com.fundit.auth.application.identity.IdentityVerificationStore;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailFindServiceUnitExceptionTest {

    @Mock private AccountRepository accountRepository;
    @Mock private IdentityVerificationStore identityVerificationStore;
    @Mock private FindEmailAttemptLimiter attemptLimiter;

    @InjectMocks private EmailFindService emailFindService;

    @Test
    void 시도_한도를_넘으면_조회하지_않고_429다() {
        // given — 번호를 바꿔가며 가입 여부를 훑는 걸 막는다
        given(attemptLimiter.exceeded("01012345678")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> emailFindService.findMasked("김펀딧", "01012345678"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.TOO_MANY_REQUESTS);
        verify(accountRepository, never()).findByNameAndPhone(any(), any());
    }

    @Test
    void 본인인증_토큰이_유효하지_않으면_401이다() {
        // given — 1회 소비라 두 번째 호출도 여기로 온다
        given(identityVerificationStore.consume("used-token")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> emailFindService.reveal("used-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.TOKEN_INVALID);
        verify(accountRepository, never()).findByNameAndPhone(anyString(), anyString());
    }

    @Test
    void 본인인증은_됐지만_가입_계정이_없으면_404다() {
        // given — 그 번호의 소유자임이 확인된 사람에게 답하는 것이라 열거가 아니다
        given(identityVerificationStore.consume("verified-token")).willReturn(Optional.of(
                new IdentityVerificationStore.VerifiedIdentity("김펀딧", "01012345678", LocalDate.of(1995, 3, 1))));
        given(accountRepository.findByNameAndPhone("김펀딧", "01012345678")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> emailFindService.reveal("verified-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }
}
