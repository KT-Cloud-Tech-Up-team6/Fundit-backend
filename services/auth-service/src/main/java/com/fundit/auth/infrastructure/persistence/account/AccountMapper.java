package com.fundit.auth.infrastructure.persistence.account;

import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.Role;
import org.springframework.stereotype.Component;

@Component
class AccountMapper {

    Account toDomain(AccountJpaEntity entity) {
        return Account.builder()
                .id(entity.getId())
                .email(entity.getEmail())
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

    AccountJpaEntity toEntity(Account domain) {
        return AccountJpaEntity.builder()
                .id(domain.getId())
                .email(domain.getEmail())
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
