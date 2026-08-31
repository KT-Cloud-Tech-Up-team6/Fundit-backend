package com.fundit.auth.infrastructure.security;

import com.fundit.auth.application.login.LoginService;
import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountAuthenticationProviderUnitTest {

    @Mock
    private LoginService loginService;

    @InjectMocks
    private AccountAuthenticationProvider provider;

    @Test
    void 인증에_성공하면_계정을_principal로_담은_토큰을_반환한다() {
        // given
        Account account = Account.builder()
                .id(UUID.randomUUID())
                .email("test@fundit.com")
                .passwordHash("hash")
                .role(Role.MEMBER)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(loginService.authenticate("test@fundit.com", "pw")).thenReturn(account);

        // when
        Authentication result = provider.authenticate(
                new UsernamePasswordAuthenticationToken("test@fundit.com", "pw"));

        // then
        assertThat(result.getPrincipal()).isEqualTo(account);
        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getAuthorities()).extracting(GrantedAuthority::getAuthority).contains("ROLE_MEMBER");
    }

    @Test
    void supports는_UsernamePasswordAuthenticationToken만_지원한다() {
        assertThat(provider.supports(UsernamePasswordAuthenticationToken.class)).isTrue();
    }
}
