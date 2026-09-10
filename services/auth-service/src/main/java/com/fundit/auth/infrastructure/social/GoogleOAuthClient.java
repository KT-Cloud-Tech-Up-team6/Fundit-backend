package com.fundit.auth.infrastructure.social;

import com.fundit.auth.application.social.SocialProviderClient;
import com.fundit.auth.domain.account.SocialProvider;
import com.fundit.common.error.DependencyFailureException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 구글 연동. userinfo 응답이 평면({@code sub}/{@code email}/{@code name})이라 카카오와 매핑이 다르다.
 *
 * <p>ID 토큰(JWT)을 파싱하지 않고 userinfo 엔드포인트를 액세스 토큰으로 호출한다 —
 * 같은 정보를 얻으면서 JWT 서명 검증 코드와 라이브러리가 통째로 필요 없어진다.
 */
@Component
public class GoogleOAuthClient implements SocialProviderClient {

    private final RestClient restClient;
    private final OAuthProperties.Provider config;

    @Autowired
    public GoogleOAuthClient(OAuthProperties properties) {
        this(OAuthTokenExchanger.restClient(), properties.getGoogle());
    }

    // 테스트 전용 — MockRestServiceServer로 감싼 RestClient를 직접 주입하기 위함.
    GoogleOAuthClient(RestClient restClient, OAuthProperties.Provider config) {
        this.restClient = restClient;
        this.config = config;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.GOOGLE;
    }

    @Override
    public SocialIdentity fetchIdentity(String authorizationCode) {
        String accessToken = OAuthTokenExchanger.exchange(restClient, config, authorizationCode);

        GoogleUserResponse response;
        try {
            response = restClient.get()
                    .uri(config.getUserInfoUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(GoogleUserResponse.class);
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }

        // 제공자 응답을 그대로 믿지 않는다(security.md S7). sub가 없으면 계정을 식별할 수 없다.
        if (response == null || response.sub() == null || response.sub().isBlank()) {
            throw new DependencyFailureException(
                    new IllegalStateException("구글이 사용자 sub를 주지 않았다"));
        }
        return new SocialIdentity(response.sub(), response.email(), response.name());
    }

    private record GoogleUserResponse(String sub, String email, String name) {
    }
}
