package com.fundit.auth.infrastructure.social;

import com.fundit.auth.application.social.SocialProviderClient;
import com.fundit.auth.domain.account.SocialProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleOAuthClientUnitTest {

    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String USER_INFO_URI = "https://www.googleapis.com/oauth2/v3/userinfo";

    private final OAuthProperties.Provider config = SocialTestFixture.config(TOKEN_URI, USER_INFO_URI);
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final GoogleOAuthClient client = new GoogleOAuthClient(builder.build(), config);

    @Test
    void 인가코드를_교환해_소셜_신원을_가져온다() {
        // given — 구글은 응답이 평면이라 카카오와 매핑이 다르다
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess("{\"access_token\": \"access-token-value\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_INFO_URI))
                .andExpect(method(GET))
                .andExpect(header("Authorization", "Bearer access-token-value"))
                .andRespond(withSuccess("""
                        { "sub": "109876543210", "email": "user@gmail.com", "name": "홍길동" }
                        """, MediaType.APPLICATION_JSON));

        // when
        SocialProviderClient.SocialIdentity identity = client.fetchIdentity("auth-code");

        // then
        assertThat(client.provider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(identity.socialId()).isEqualTo("109876543210");
        assertThat(identity.email()).isEqualTo("user@gmail.com");
        assertThat(identity.name()).isEqualTo("홍길동");
        server.verify();
    }
}
