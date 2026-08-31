package com.fundit.auth.application.identity;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityVerificationServiceExceptionTest {

    @Mock
    private PortOneClient portOneClient;
    @Mock
    private IdentityVerificationStore store;

    @Test
    void PortOne_인증이_미완료면_예외가_발생한다() {
        // given
        var service = new IdentityVerificationService(portOneClient, store, Duration.ofMinutes(30));
        when(portOneClient.fetchVerification("identity-verification-1")).thenReturn(
                new PortOneClient.VerifiedIdentityResult(false, null, null, null, null, null));

        // when & then
        assertThatThrownBy(() -> service.verify("identity-verification-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.TOKEN_INVALID);
        verify(store, never()).save(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
