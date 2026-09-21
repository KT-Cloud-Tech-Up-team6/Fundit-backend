package com.fundit.auth.infrastructure.seed;

import com.fundit.auth.application.signup.SignupService;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QaTestAccountSeederUnitExceptionTest {

    @Mock
    private SignupService signupService;
    @Mock
    private AccountRepository accountRepository;

    @Test
    void member_서비스가_실패해도_기동을_막지_않고_다음_계정을_시도한다() {
        // given
        when(accountRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(signupService.createVerifiedAccount(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyList(), any())).thenThrow(new DependencyFailureException(new RuntimeException()));
        QaTestAccountSeeder seeder = new QaTestAccountSeeder(signupService, accountRepository, "qa-password");

        // when & then
        assertThatCode(() -> seeder.run(null)).doesNotThrowAnyException();
        verify(signupService, times(2)).createVerifiedAccount(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyList(), any());
    }
}
