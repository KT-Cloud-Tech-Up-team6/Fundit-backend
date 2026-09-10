package com.fundit.auth.infrastructure.social;

import com.fundit.auth.application.social.SocialProviderClient;
import com.fundit.auth.domain.account.SocialProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KakaoOAuthClientUnitTest {

    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

    private final OAuthProperties.Provider config = SocialTestFixture.config(TOKEN_URI, USER_INFO_URI);
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final KakaoOAuthClient client = new KakaoOAuthClient(builder.build(), config);

    @Test
    void 인가코드를_교환해_소셜_신원을_가져온다() {
        // given
        expectTokenExchange();
        server.expect(requestTo(USER_INFO_URI))
                .andExpect(method(GET))
                .andExpect(header("Authorization", "Bearer access-token-value"))
                .andRespond(withSuccess("""
                        {
                          "id": 1234567890,
                          "kakao_account": {
                            "email": "user@kakao.com",
                            "profile": { "nickname": "응원왕" }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        // when
        SocialProviderClient.SocialIdentity identity = client.fetchIdentity("auth-code");

        // then
        assertThat(client.provider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(identity.socialId()).isEqualTo("1234567890");
        assertThat(identity.email()).isEqualTo("user@kakao.com");
        assertThat(identity.name()).isEqualTo("응원왕");
        server.verify();
    }

    @Test
    void 이메일_제공에_동의하지_않았으면_이메일_없이_돌려준다() {
        // given — 카카오는 이메일 동의가 선택이라 kakao_account가 통째로 비어 올 수 있다.
        // 여기서 예외를 던지면 가입 자체가 막힌다 — 없는 값은 가입 화면에서 받는다(PM 확정)
        expectTokenExchange();
        server.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess("{\"id\": 1234567890}", MediaType.APPLICATION_JSON));

        // when
        SocialProviderClient.SocialIdentity identity = client.fetchIdentity("auth-code");

        // then
        assertThat(identity.socialId()).isEqualTo("1234567890");
        assertThat(identity.email()).isNull();
        assertThat(identity.name()).isNull();
    }

    private void expectTokenExchange() {
        server.expect(requestTo(TOKEN_URI))
                .andExpect(method(POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("code=auth-code")))
                // redirect_uri는 클라이언트가 보낸 값이 아니라 서버 설정값이어야 한다(security.md S7)
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("redirect_uri=https%3A%2F%2Fapp.fundit.kr%2Foauth")))
                .andRespond(withSuccess("{\"access_token\": \"access-token-value\"}", MediaType.APPLICATION_JSON));
    }
}
