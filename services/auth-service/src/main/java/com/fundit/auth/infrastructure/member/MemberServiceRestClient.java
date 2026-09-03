package com.fundit.auth.infrastructure.member;

import com.fundit.auth.application.signup.MemberServiceClient;
import com.fundit.common.error.DependencyFailureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

/**
 * member-service 동기 호출 어댑터. CLAUDE.md 규칙대로 connect/read 타임아웃을 명시 설정한다
 * (프레임워크 기본값 그대로 두지 않음). member-service가 아직 레포에 없어 로컬/dev에서는
 * 이 호출이 항상 실패한다 — 의도된 상태(DEPENDENCY_FAILURE로 응답).
 */
@Component
public class MemberServiceRestClient implements MemberServiceClient {

    private final RestClient restClient;

    public MemberServiceRestClient(@Value("${member-service.base-url}") String baseUrl) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
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
