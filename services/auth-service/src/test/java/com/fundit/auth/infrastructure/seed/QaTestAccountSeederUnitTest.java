package com.fundit.auth.infrastructure.seed;

import com.fundit.auth.application.signup.SignupService;
import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    void 비밀번호가_비어_있으면_아무것도_만들지_않는다() {
        // given — local·prod는 키가 없어 빈 값이다
        QaTestAccountSeeder disabled = new QaTestAccountSeeder(signupService, accountRepository, "");

        // when
        disabled.run(null);

        // then
        verify(accountRepository, never()).findByEmail(anyString());
        verify(signupService, never()).createVerifiedAccount(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyList(), any());
    }
}
