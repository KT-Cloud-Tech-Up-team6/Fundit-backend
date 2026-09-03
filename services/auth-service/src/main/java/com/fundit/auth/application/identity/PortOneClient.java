package com.fundit.auth.application.identity;

import java.time.LocalDate;

public interface PortOneClient {

    VerifiedIdentityResult fetchVerification(String identityVerificationId);

    record VerifiedIdentityResult(
            boolean verified,
            String name,
            String phoneNumber,
            LocalDate birthDate,
            String ci,
            String di
    ) {
    }
}
