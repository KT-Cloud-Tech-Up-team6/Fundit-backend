package com.fundit.auth.domain.account;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {

    Account save(Account account);

    Optional<Account> findById(UUID id);

    Optional<Account> findByEmail(String email);

    boolean existsByEmail(String email);

    void deleteById(UUID id);
}
