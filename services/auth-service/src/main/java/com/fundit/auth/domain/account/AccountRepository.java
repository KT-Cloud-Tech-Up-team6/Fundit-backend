package com.fundit.auth.domain.account;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {

    Account save(Account account);

    Optional<Account> findById(UUID id);

    Optional<Account> findByEmail(String email);

    boolean existsByEmail(String email);

    void deleteById(UUID id);

    /** 계정 행에 pessimistic write lock을 건다(존재하지 않으면 잠글 행이 없어 no-op). */
    void lockForUpdate(UUID id);
}
