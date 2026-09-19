package com.fundit.auth.domain.account;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {

    Account save(Account account);

    Optional<Account> findById(UUID id);

    Optional<Account> findByEmail(String email);

    Optional<Account> findBySocial(SocialProvider provider, String socialId);

    /**
     * 이메일 찾기(AUTH-009) — 이름과 전화번호가 모두 일치하는 계정.
     * 평문을 받아 구현체가 블라인드 인덱스로 바꿔 조회한다.
     */
    Optional<Account> findByNameAndPhone(String name, String phoneNumber);

    boolean existsByEmail(String email);

    void deleteById(UUID id);

    /** 계정 행에 pessimistic write lock을 건다(존재하지 않으면 잠글 행이 없어 no-op). */
    void lockForUpdate(UUID id);
}
