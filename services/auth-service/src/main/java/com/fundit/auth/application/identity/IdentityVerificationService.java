package com.fundit.auth.application.identity;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class IdentityVerificationService {

    private final PortOneClient portOneClient;
    private final IdentityVerificationStore store;
    private final Duration tokenTtl;

    public IdentityVerificationService(
            PortOneClient portOneClient,
            IdentityVerificationStore store,
            @Value("${identity-verification.token-ttl}") Duration tokenTtl) {
        this.portOneClient = portOneClient;
        this.store = store;
        this.tokenTtl = tokenTtl;
    }

    public IdentityVerificationResult verify(String identityVerificationId) {
        var result = portOneClient.fetchVerification(identityVerificationId);
        if (!result.verified()) {
            throw new BusinessException(CommonErrorCode.TOKEN_INVALID);
        }

        String verificationToken = UUID.randomUUID().toString();
        store.save(verificationToken,
                new IdentityVerificationStore.VerifiedIdentity(result.name(), result.phoneNumber(), result.birthDate()),
                tokenTtl);

        return new IdentityVerificationResult(verificationToken, Instant.now().plus(tokenTtl));
    }

    public record IdentityVerificationResult(String verificationToken, Instant expiresAt) {
    }
}
