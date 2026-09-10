package com.fundit.auth.infrastructure.social;

import com.fundit.common.error.DependencyFailureException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 제공자 응답 실패는 전부 503(DEPENDENCY_FAILURE)으로 나가야 한다 — 명세 AUTH-002.
 * 카카오/구글이 같은 경로를 타므로 한쪽으로만 검증한다.
 */
class SocialOAuthClientUnitExceptionTest {

    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

    private final OAuthProperties.Provider config = SocialTestFixture.config(TOKEN_URI, USER_INFO_URI);
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final KakaoOAuthClient client = new KakaoOAuthClient(builder.build(), config);

    @Test
    void 인가코드가_만료됐거나_이미_쓰였으면_503으로_변환한다() {
        // given — OAuth 인가 코드는 1회용이라 재시도하면 제공자가 400을 준다
        server.expect(requestTo(TOKEN_URI)).andRespond(withBadRequest());

        // when & then
        assertThatThrownBy(() -> client.fetchIdentity("used-code"))
                .isInstanceOf(DependencyFailureException.class);
    }

    @Test
    void 액세스_토큰이_비어_오면_503으로_끊는다() {
        // given — 200이라고 무조건 믿지 않는다(security.md S7)
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.fetchIdentity("auth-code"))
                .isInstanceOf(DependencyFailureException.class);
    }

    @Test
    void 사용자_id가_없으면_503으로_끊는다() {
        // given — id가 없으면 어떤 계정인지 식별할 수 없어 로그인시켜선 안 된다
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess("{\"access_token\": \"t\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess("{\"kakao_account\": {\"email\": \"a@b.c\"}}", MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.fetchIdentity("auth-code"))
                .isInstanceOf(DependencyFailureException.class);
    }
}
