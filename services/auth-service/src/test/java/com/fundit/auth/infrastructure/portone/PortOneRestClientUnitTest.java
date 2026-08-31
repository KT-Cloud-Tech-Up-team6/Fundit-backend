package com.fundit.auth.infrastructure.portone;

import com.fundit.auth.application.identity.PortOneClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PortOneRestClientUnitTest {

    private static final String API_SECRET = "test-api-secret";
    private static final String STORE_ID = "test-store-id";

    @Test
    void 인증완료_응답이면_검증된_고객정보를_반환한다() {
        // given
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.portone.io");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PortOneRestClient client = new PortOneRestClient(builder.build(), API_SECRET, STORE_ID);

        server.expect(requestTo("https://api.portone.io/identity-verifications/identity-verification-1?storeId=" + STORE_ID))
                .andExpect(header("Authorization", "PortOne " + API_SECRET))
                .andRespond(withSuccess("""
                        {
                          "status": "VERIFIED",
                          "verifiedCustomer": {
                            "name": "홍길동",
                            "phoneNumber": "01012345678",
                            "birthDate": "1999-01-01",
                            "ci": "ci-value",
                            "di": "di-value"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        // when
        PortOneClient.VerifiedIdentityResult result = client.fetchVerification("identity-verification-1");

        // then
        assertThat(result.verified()).isTrue();
        assertThat(result.name()).isEqualTo("홍길동");
        assertThat(result.phoneNumber()).isEqualTo("01012345678");
        assertThat(result.birthDate()).isEqualTo(LocalDate.of(1999, 1, 1));
        server.verify();
    }

    @Test
    void 인증상태가_VERIFIED가_아니면_미검증으로_반환한다() {
        // given
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.portone.io");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PortOneRestClient client = new PortOneRestClient(builder.build(), API_SECRET, STORE_ID);

        server.expect(requestTo("https://api.portone.io/identity-verifications/identity-verification-2?storeId=" + STORE_ID))
                .andRespond(withSuccess("""
                        {"status": "FAILED", "verifiedCustomer": null}
                        """, MediaType.APPLICATION_JSON));

        // when
        PortOneClient.VerifiedIdentityResult result = client.fetchVerification("identity-verification-2");

        // then
        assertThat(result.verified()).isFalse();
    }
}
