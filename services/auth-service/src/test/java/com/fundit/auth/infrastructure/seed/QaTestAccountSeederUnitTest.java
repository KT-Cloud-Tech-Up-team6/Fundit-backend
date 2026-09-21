package com.fundit.auth.infrastructure.seed;

import com.fundit.auth.application.signup.SignupService;
import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.common.error.DependencyFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QaTestAccountSeederUnitTest {

    @Mock
    private SignupService signupService;
    @Mock
    private AccountRepository accountRepository;

    private QaTestAccountSeeder seeder() {
        return new QaTestAccountSeeder(signupService, accountRepository, "qa-password");
    }

    @Test
    void 없는_계정만_만든다() {
        // given — 판매자 계정은 이미 있고 서포터 계정은 없다
        when(accountRepository.findByEmail("qa-seller@infrastudy.store"))
                .thenReturn(Optional.of(Account.builder().email("qa-seller@infrastudy.store").build()));
        when(accountRepository.findByEmail("qa-supporter@infrastudy.store")).thenReturn(Optional.empty());

        // when
        seeder().run(null);

        // then — 재배포해도 중복 생성되지 않는다
        verify(signupService, never()).createVerifiedAccount(eq("qa-seller@infrastudy.store"),
                anyString(), anyString(), anyString(), anyString(), anyList(), any());
        verify(signupService).createVerifiedAccount(eq("qa-supporter@infrastudy.store"), eq("qa-password"),
                anyString(), anyString(), anyString(), anyList(), any());
    }

    @Test
    void member_서비스가_실패해도_기동을_막지_않고_다음_계정을_시도한다() {
        // given
        when(accountRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(signupService.createVerifiedAccount(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyList(), any())).thenThrow(new DependencyFailureException(new RuntimeException()));

        // when & then
        assertThatCode(() -> seeder().run(null)).doesNotThrowAnyException();
        verify(signupService, times(2)).createVerifiedAccount(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyList(), any());
    }
}
