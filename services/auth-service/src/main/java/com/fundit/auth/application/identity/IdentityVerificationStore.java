package com.fundit.auth.application.identity;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

public interface IdentityVerificationStore {

    void save(String verificationToken, VerifiedIdentity identity, Duration ttl);

    /**
     * 1회 소비(get-and-delete) — 같은 verificationToken으로 두 번 조회하면 두 번째는 항상 비어있다.
     */
    Optional<VerifiedIdentity> consume(String verificationToken);

    record VerifiedIdentity(String name, String phoneNumber, LocalDate birthDate) {
    }
}
