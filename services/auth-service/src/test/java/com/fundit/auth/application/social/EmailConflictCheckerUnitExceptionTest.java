package com.fundit.auth.application.social;

import com.fundit.auth.domain.AuthErrorCode;
import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.auth.domain.account.Role;
import com.fundit.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** PM 확정 이메일 충돌 정책 A·B·C의 판정부. 두 진입점(소셜 로그인/가입)이 이 결과를 공유한다. */
@ExtendWith(MockitoExtension.class)
class EmailConflictCheckerUnitExceptionTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private EmailConflictChecker checker;

    @Test
    void 소셜로_가입된_이메일이면_어느_제공자인지_알려주고_막는다() {
        // given — 정책 B·C. 사용자가 "어디로 로그인해야 하는지" 알아야 다음 행동이 가능하다
        when(accountRepository.findByEmail("user@x.com"))
                .thenReturn(Optional.of(account("user@x.com", "KAKAO")));

        // when & then
        assertThatThrownBy(() -> checker.checkOrThrow("user@x.com"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(AuthErrorCode.SOCIAL_ACCOUNT_EXISTS);
                    // 클라이언트가 메시지 문장을 파싱하지 않고 분기할 수 있어야 한다
                    assertThat(be.getDetail()).isEqualTo(Map.of("provider", "KAKAO"));
                    assertThat(be.getMessage()).contains("KAKAO");
                });
    }

    @Test
    void 자체가입_계정이면_막지_않고_연동_대상으로_돌려준다() {
        // given — 정책 A. 본인인증 후 연동할 수 있으므로 여기서 예외를 던지면 안 된다
        Account local = account("user@x.com", null);
        when(accountRepository.findByEmail("user@x.com")).thenReturn(Optional.of(local));

        // when
        Account result = checker.checkOrThrow("user@x.com");

        // then
        assertThat(result).isSameAs(local);
    }

    @Test
    void 이메일이_없으면_판정하지_않는다() {
        // given — 카카오 이메일 미동의. 가입 화면에서 받은 뒤 다시 판정한다

        // when & then
        assertThat(checker.checkOrThrow(null)).isNull();
        assertThat(checker.checkOrThrow("  ")).isNull();
    }

    private Account account(String email, String socialProvider) {
        return Account.builder()
                .id(UUID.randomUUID()).email(email)
                .socialProvider(socialProvider).socialId(socialProvider == null ? null : "social-1")
                .role(Role.MEMBER).failedLoginCount(0).mustChangePassword(false)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
    }
}
