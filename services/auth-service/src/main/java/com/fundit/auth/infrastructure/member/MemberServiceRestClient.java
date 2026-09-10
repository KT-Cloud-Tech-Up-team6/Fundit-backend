package com.fundit.auth.infrastructure.member;

import com.fundit.auth.application.signup.MemberServiceClient;
import com.fundit.common.error.DependencyFailureException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.UUID;

/**
 * member-service 동기 호출 어댑터. CLAUDE.md 규칙대로 connect/read 타임아웃을 명시 설정한다
 * (프레임워크 기본값 그대로 두지 않음). member-service가 내부 전용 엔드포인트 방어로 요구하는
 * X-Internal-Api-Key 헤더를 함께 보낸다(security.md S7 — 시크릿은 설정값으로 분리).
 */
@Component
public class MemberServiceRestClient implements MemberServiceClient {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final RestClient restClient;

    @Autowired
    public MemberServiceRestClient(
            @Value("${member-service.base-url}") String baseUrl,
            @Value("${internal-api.key}") String internalApiKey) {
        this(buildRestClient(baseUrl, internalApiKey));
    }

    // 테스트 전용 — MockRestServiceServer로 감싼 RestClient를 직접 주입하기 위함.
    MemberServiceRestClient(RestClient restClient) {
        this.restClient = restClient;
    }

    private static RestClient buildRestClient(String baseUrl, String internalApiKey) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(INTERNAL_API_KEY_HEADER, internalApiKey)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public boolean phoneMatches(UUID accountId, String phoneNumber) {
        try {
            PhoneVerificationResponse response = restClient.post()
                    .uri("/api/v1/members/{accountId}/phone-verification", accountId)
                    .body(new PhoneVerificationRequest(phoneNumber))
                    .retrieve()
                    .body(PhoneVerificationResponse.class);
            return response != null && response.matches();
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    /** 휴대폰번호를 URL이 아니라 본문으로 보낸다 — 쿼리스트링은 액세스 로그에 남는다(security.md S10). */
    private record PhoneVerificationRequest(String phoneNumber) {
    }

    private record PhoneVerificationResponse(boolean matches) {
    }

    @Override
    public MemberProfile createProfile(CreateMemberProfileCommand command) {
        try {
            return restClient.post()
                    .uri("/api/v1/members")
                    .body(command)
                    .retrieve()
                    .body(MemberProfile.class);
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }
}
