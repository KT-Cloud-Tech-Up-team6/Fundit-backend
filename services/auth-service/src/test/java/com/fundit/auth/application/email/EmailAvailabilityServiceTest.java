package com.fundit.auth.application.email;

import com.fundit.auth.domain.account.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailAvailabilityServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private EmailAvailabilityService emailAvailabilityService;

    @Test
    void 이미_존재하는_이메일이면_사용불가를_반환한다() {
        // given
        when(accountRepository.existsByEmail("dup@fundit.com")).thenReturn(true);

        // when
        boolean available = emailAvailabilityService.isAvailable("dup@fundit.com");

        // then
        assertThat(available).isFalse();
    }

    @Test
    void 존재하지_않는_이메일이면_사용가능을_반환한다() {
        // given
        when(accountRepository.existsByEmail("new@fundit.com")).thenReturn(false);

        // when
        boolean available = emailAvailabilityService.isAvailable("new@fundit.com");

        // then
        assertThat(available).isTrue();
    }
}
