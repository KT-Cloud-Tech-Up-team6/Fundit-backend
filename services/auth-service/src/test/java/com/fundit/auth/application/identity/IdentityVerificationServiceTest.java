package com.fundit.auth.application.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityVerificationServiceTest {

    @Mock
    private PortOneClient portOneClient;
    @Mock
    private IdentityVerificationStore store;

    @Test
    void 인증이_완료됐으면_검증정보를_저장하고_토큰을_발급한다() {
        // given
        var service = new IdentityVerificationService(portOneClient, store, Duration.ofMinutes(30));
        when(portOneClient.fetchVerification("identity-verification-1")).thenReturn(
                new PortOneClient.VerifiedIdentityResult(
                        true, "홍길동", "01012345678", LocalDate.of(1999, 1, 1), "ci-value", "di-value"));

        // when
        var result = service.verify("identity-verification-1");

        // then
        assertThat(result.verificationToken()).isNotBlank();
        verify(store).save(eq(result.verificationToken()), any(IdentityVerificationStore.VerifiedIdentity.class),
                eq(Duration.ofMinutes(30)));
    }
}
