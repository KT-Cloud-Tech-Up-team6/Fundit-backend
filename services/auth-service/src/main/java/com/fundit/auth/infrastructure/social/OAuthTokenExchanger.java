package com.fundit.auth.infrastructure.social;

import com.fundit.common.error.DependencyFailureException;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

/**
 * 인가 코드 → 액세스 토큰 교환. 카카오·구글이 이 부분만은 같은 모양(RFC 6749 authorization_code)이라
 * 두 클라이언트가 공유한다. 사용자정보 응답은 구조가 달라 각자 처리한다.
 *
 * <p>상속 대신 이 클래스를 갖다 쓴다 — 구현이 둘뿐인데 템플릿 메서드까지 만들 이유가 없다.
 */
final class OAuthTokenExchanger {

    private OAuthTokenExchanger() {
    }

    /** CLAUDE.md 규칙 — 서비스 간/외부 동기 호출에 프레임워크 기본 타임아웃을 그대로 두지 않는다. */
    static RestClient restClient() {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    static String exchange(RestClient restClient, OAuthProperties.Provider config, String authorizationCode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", config.getClientId());
        form.add("client_secret", config.getClientSecret());
        form.add("redirect_uri", config.getRedirectUri());
        form.add("code", authorizationCode);

        TokenResponse response;
        try {
            response = restClient.post()
                    .uri(config.getTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientException e) {
            // 인가 코드 만료·재사용도 여기로 온다. 제공자 응답 실패는 503(명세 AUTH-002).
            throw new DependencyFailureException(e);
        }

        if (response == null || response.access_token() == null || response.access_token().isBlank()) {
            throw new DependencyFailureException(
                    new IllegalStateException("소셜 제공자가 액세스 토큰을 주지 않았다"));
        }
        return response.access_token();
    }

    /** 필드명을 와이어 포맷 그대로 둔다 — 이 레포는 Jackson 애노테이션을 쓰지 않는다. */
    private record TokenResponse(String access_token) {
    }
}
