package com.fundit.auth.infrastructure.persistence.account;

import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.Role;
import com.fundit.auth.infrastructure.security.AesGcmCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 도메인은 평문, 엔티티는 암호문이다(security.md S9). 경계가 여기 한 곳이라
 * 애플리케이션 계층은 암호화를 모른다 — payment-service {@code RefundRequestMapper}와 같은 형태다.
 */
@Component
@RequiredArgsConstructor
class AccountMapper {

    private final AesGcmCipher cipher;

    Account toDomain(AccountJpaEntity entity) {
        return Account.builder()
                .id(entity.getId())
                .email(cipher.decrypt(entity.getEmail()))
                .passwordHash(entity.getPasswordHash())
                .socialProvider(entity.getSocialProvider())
                .socialId(entity.getSocialId())
                .role(Role.valueOf(entity.getRole().toUpperCase()))
                .failedLoginCount(entity.getFailedLoginCount())
                .lockedUntil(entity.getLockedUntil())
                .mustChangePassword(entity.getMustChangePassword())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * 해시는 여기서 만들지 않고 어댑터가 {@code BlindIndex}로 계산해 넘긴다 —
     * 저장 경로와 조회 경로가 같은 계산을 쓰는지 한 곳에서 보이게 하기 위해서다.
     */
    AccountJpaEntity toEntity(Account domain, String emailHash, String phoneHash, String nameHash) {
        return AccountJpaEntity.builder()
                .id(domain.getId())
                .email(cipher.encrypt(domain.getEmail()))
                .emailHash(emailHash)
                .phoneHash(phoneHash)
                .nameHash(nameHash)
                .passwordHash(domain.getPasswordHash())
                .socialProvider(domain.getSocialProvider())
                .socialId(domain.getSocialId())
                .role(domain.getRole().name().toLowerCase())
                .failedLoginCount(domain.getFailedLoginCount())
                .lockedUntil(domain.getLockedUntil())
                .mustChangePassword(domain.isMustChangePassword())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }
}
