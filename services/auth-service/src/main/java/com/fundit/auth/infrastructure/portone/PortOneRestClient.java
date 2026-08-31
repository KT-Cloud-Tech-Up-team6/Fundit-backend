package com.fundit.auth.infrastructure.portone;

import com.fundit.auth.application.identity.PortOneClient;
import com.fundit.common.error.DependencyFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.LocalDate;

/**
 * PortOne 본인인증 단건조회 어댑터. MemberServiceRestClient와 동일하게 connect/read 타임아웃을
 * 명시 설정한다(CLAUDE.md 규칙). PortOne 응답은 신뢰하지 않고 status/필드 존재 여부를 확인한 뒤
 * 사용한다(security.md S7).
 */
@Component
public class PortOneRestClient implements PortOneClient {

    private static final String VERIFIED_STATUS = "VERIFIED";

    private final RestClient restClient;
    private final String apiSecret;
    private final String storeId;

    public PortOneRestClient(PortOneProperties properties) {
        this(buildRestClient(properties.getBaseUrl()), properties.getApiSecret(), properties.getStoreId());
    }

    // 테스트 전용 — MockRestServiceServer로 감싼 RestClient를 직접 주입하기 위함.
    PortOneRestClient(RestClient restClient, String apiSecret, String storeId) {
        this.restClient = restClient;
        this.apiSecret = apiSecret;
        this.storeId = storeId;
    }

    private static RestClient buildRestClient(String baseUrl) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public VerifiedIdentityResult fetchVerification(String identityVerificationId) {
        PortOneIdentityVerificationResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/identity-verifications/{id}")
                            .queryParam("storeId", storeId)
                            .build(identityVerificationId))
                    .header(HttpHeaders.AUTHORIZATION, "PortOne " + apiSecret)
                    .retrieve()
                    .body(PortOneIdentityVerificationResponse.class);
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }

        if (response == null || !VERIFIED_STATUS.equals(response.status()) || response.verifiedCustomer() == null) {
            return new VerifiedIdentityResult(false, null, null, null, null, null);
        }

        VerifiedCustomer customer = response.verifiedCustomer();
        return new VerifiedIdentityResult(
                true,
                customer.name(),
                customer.phoneNumber(),
                customer.birthDate() == null ? null : LocalDate.parse(customer.birthDate()),
                customer.ci(),
                customer.di());
    }

    private record PortOneIdentityVerificationResponse(String status, VerifiedCustomer verifiedCustomer) {
    }

    private record VerifiedCustomer(String name, String phoneNumber, String birthDate, String ci, String di) {
    }
}
