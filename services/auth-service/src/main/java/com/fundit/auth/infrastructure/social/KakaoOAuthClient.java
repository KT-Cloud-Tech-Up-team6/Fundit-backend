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
 * 카카오 연동. 사용자정보 응답이 {@code kakao_account} 아래로 한 겹 더 들어가 있어 구글과 매핑이 다르다.
 *
 * <p>이메일은 <b>없을 수 있다</b> — 카카오는 이메일 제공 동의가 선택이고 비즈니스 앱 심사가 필요하다.
 * 없으면 그대로 null로 넘기고, 가입 화면에서 사용자에게 직접 받는다(PM 확정).
 */
@Component
public class KakaoOAuthClient implements SocialProviderClient {

    private final RestClient restClient;
    private final OAuthProperties.Provider config;

    @Autowired
    public KakaoOAuthClient(OAuthProperties properties) {
        this(OAuthTokenExchanger.restClient(), properties.getKakao());
    }

    // 테스트 전용 — MockRestServiceServer로 감싼 RestClient를 직접 주입하기 위함.
    KakaoOAuthClient(RestClient restClient, OAuthProperties.Provider config) {
        this.restClient = restClient;
        this.config = config;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.KAKAO;
    }

    @Override
    public SocialIdentity fetchIdentity(String authorizationCode) {
        String accessToken = OAuthTokenExchanger.exchange(restClient, config, authorizationCode);

        KakaoUserResponse response;
        try {
            response = restClient.get()
                    .uri(config.getUserInfoUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(KakaoUserResponse.class);
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }

        // 제공자 응답을 그대로 믿지 않는다(security.md S7). id가 없으면 계정을 식별할 수 없다.
        if (response == null || response.id() == null) {
            throw new DependencyFailureException(
                    new IllegalStateException("카카오가 사용자 id를 주지 않았다"));
        }

        KakaoAccount account = response.kakao_account();
        String email = account == null ? null : account.email();
        String name = account == null || account.profile() == null ? null : account.profile().nickname();
        return new SocialIdentity(String.valueOf(response.id()), email, name);
    }

    private record KakaoUserResponse(Long id, KakaoAccount kakao_account) {
    }

    private record KakaoAccount(String email, KakaoProfile profile) {
    }

    private record KakaoProfile(String nickname) {
    }
}
