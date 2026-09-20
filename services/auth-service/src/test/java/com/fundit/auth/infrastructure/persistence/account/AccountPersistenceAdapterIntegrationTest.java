package com.fundit.auth.infrastructure.persistence.account;

import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.auth.domain.account.Role;
import com.fundit.auth.infrastructure.security.JwtTestKeys;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>DB에 평문이 남지 않는지</b>를 실제 Postgres로 확인한다(security.md S9).
 *
 * <p>JPA로 읽으면 Mapper가 복호화해서 돌려주므로 암호화를 하든 안 하든 테스트가 통과한다.
 * 그래서 여기서는 <b>네이티브 쿼리로 raw 컬럼 값을 직접 읽는다.</b>
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "auth.encryption.key=Oz9qy5geAzhDXyHfZdFB3WPwVH8/sx/uD2j5rX5DGkY=",
        "member-service.base-url=http://localhost:8082",
        "internal-api.key=test-only-internal-api-key"
})
@Transactional
class AccountPersistenceAdapterIntegrationTest {

    @DynamicPropertySource
    static void jwtKey(DynamicPropertyRegistry registry) {
        JwtTestKeys.register(registry);
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired private AccountRepository accountRepository;
    @Autowired private EntityManager entityManager;

    private Account newAccount(String email) {
        Instant now = Instant.now();
        return Account.builder()
                .id(UUID.randomUUID())
                .email(email)
                .verifiedName("김펀딧")
                .verifiedPhoneNumber("010-1234-5678")
                .passwordHash("bcrypt-hash")
                .role(Role.MEMBER)
                .failedLoginCount(0)
                .mustChangePassword(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private String rawColumn(UUID id, String column) {
        entityManager.flush();
        entityManager.clear();
        return (String) entityManager
                .createNativeQuery("select " + column + " from accounts where id = :id")
                .setParameter("id", id)
                .getSingleResult();
    }

    @Test
    void 이메일은_평문으로_저장되지_않는다() {
        // given
        Account saved = accountRepository.save(newAccount("plain@fundit.com"));

        // when — JPA로 읽으면 복호화돼서 통과해버린다. raw 컬럼을 직접 본다
        String stored = rawColumn(saved.getId(), "email");

        // then
        assertThat(stored).isNotEqualTo("plain@fundit.com");
        assertThat(stored).doesNotContain("plain@fundit.com");
    }

    @Test
    void 이름과_전화번호는_해시만_남고_평문은_저장되지_않는다() {
        // given — 원문은 member-service 소관이고 auth는 찾을 수만 있으면 된다
        Account saved = accountRepository.save(newAccount("hash@fundit.com"));

        // when
        String phoneHash = rawColumn(saved.getId(), "phone_hash");
        String nameHash = rawColumn(saved.getId(), "name_hash");

        // then
        assertThat(phoneHash).hasSize(64).doesNotContain("1234");
        assertThat(nameHash).hasSize(64).doesNotContain("김펀딧");
    }

    @Test
    void 암호화해도_이메일로_조회된다() {
        // given
        accountRepository.save(newAccount("lookup@fundit.com"));
        entityManager.flush();
        entityManager.clear();

        // when — 블라인드 인덱스를 거쳐 찾는다
        var found = accountRepository.findByEmail("lookup@fundit.com");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("lookup@fundit.com");
        assertThat(accountRepository.existsByEmail("lookup@fundit.com")).isTrue();
    }

    @Test
    void 대소문자가_달라도_같은_계정으로_조회된다() {
        // given — 가입 때와 조회 때 표기가 갈리면 계정을 못 찾는다
        accountRepository.save(newAccount("Mixed@Fundit.com"));
        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThat(accountRepository.findByEmail("mixed@fundit.com")).isPresent();
    }

    @Test
    void 이름과_전화번호로_계정을_찾는다() {
        // given — 이메일 찾기(AUTH-009)가 쓰는 경로다
        accountRepository.save(newAccount("find@fundit.com"));
        entityManager.flush();
        entityManager.clear();

        // when — 표기가 달라도 숫자만 비교한다
        var found = accountRepository.findByNameAndPhone("김펀딧", "01012345678");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("find@fundit.com");
    }

    @Test
    void 동명이인_계정이_있어도_예외_없이_가장_최근_계정을_돌려준다() {
        // given — SignupService는 이메일 중복만 막고 이름+전화번호 중복은 막지 않는다.
        // 같은 사람이 이메일만 바꿔 두 번 가입하면 phone_hash·name_hash가 같은 계정이 2개
        // 생기는데, Optional 반환 파생 쿼리였다면 여기서 IncorrectResultSizeDataAccessException이
        // 났다(실제로 재현되던 버그).
        accountRepository.save(newAccount("first@fundit.com"));
        Account second = accountRepository.save(newAccount("second@fundit.com"));
        entityManager.flush();
        entityManager.clear();

        // when — 예외 없이 하나를 돌려줘야 한다
        var found = accountRepository.findByNameAndPhone("김펀딧", "01012345678");

        // then — 가장 최근(나중에 저장한) 계정을 고른다
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(second.getId());
    }

    @Test
    void 갱신_저장이_이름_전화번호_해시를_지우지_않는다() {
        // given — 읽어온 Account에는 평문 이름·전화번호가 없다(해시는 되돌릴 수 없다).
        // 그 상태로 저장하면 해시가 null로 덮여 이메일 찾기가 조용히 망가진다
        Account saved = accountRepository.save(newAccount("keep@fundit.com"));
        entityManager.flush();
        entityManager.clear();

        // when — 비밀번호만 바꿔 다시 저장
        Account loaded = accountRepository.findById(saved.getId()).orElseThrow();
        loaded.changePassword("new-hash");
        accountRepository.save(loaded);
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(accountRepository.findByNameAndPhone("김펀딧", "01012345678")).isPresent();
    }
}
