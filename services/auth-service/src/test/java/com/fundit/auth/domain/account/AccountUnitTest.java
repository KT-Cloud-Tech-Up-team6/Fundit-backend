package com.fundit.auth.domain.account;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AccountUnitTest {

    private Account newAccount() {
        return Account.builder()
                .id(java.util.UUID.randomUUID())
                .email("test@fundit.com")
                .passwordHash("hash")
                .role(Role.MEMBER)
                .failedLoginCount(0)
                .mustChangePassword(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    class 로그인_실패_기록 {

        @Test
        void _5회_미만이면_잠기지_않는다() {
            // given
            Account account = newAccount();
            Instant now = Instant.now();

            // when
            for (int i = 0; i < 4; i++) {
                account.recordFailedLogin(now);
            }

            // then
            assertThat(account.getFailedLoginCount()).isEqualTo(4);
            assertThat(account.isLocked(now)).isFalse();
        }

        @Test
        void _5회째에_30분_잠금이_걸리고_카운터가_리셋된다() {
            // given
            Account account = newAccount();
            Instant now = Instant.now();

            // when
            for (int i = 0; i < 5; i++) {
                account.recordFailedLogin(now);
            }

            // then
            assertThat(account.getFailedLoginCount()).isZero();
            assertThat(account.isLocked(now)).isTrue();
            assertThat(account.getLockedUntil()).isEqualTo(now.plus(Duration.ofMinutes(30)));
        }
    }

    @Nested
    class 로그인_성공_기록 {

        @Test
        void 실패_카운터와_잠금이_초기화된다() {
            // given
            Account account = newAccount();
            Instant now = Instant.now();
            for (int i = 0; i < 4; i++) {
                account.recordFailedLogin(now);
            }

            // when
            account.recordSuccessfulLogin();

            // then
            assertThat(account.getFailedLoginCount()).isZero();
            assertThat(account.getLockedUntil()).isNull();
        }
    }

    @Nested
    class 비밀번호_변경 {

        @Test
        void 해시가_교체되고_강제변경_플래그가_해제된다() {
            // given
            Account account = Account.builder()
                    .id(java.util.UUID.randomUUID())
                    .email("test@fundit.com")
                    .passwordHash("old-hash")
                    .role(Role.MEMBER)
                    .mustChangePassword(true)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            // when
            account.changePassword("new-hash");

            // then
            assertThat(account.getPasswordHash()).isEqualTo("new-hash");
            assertThat(account.isMustChangePassword()).isFalse();
        }
    }
}
