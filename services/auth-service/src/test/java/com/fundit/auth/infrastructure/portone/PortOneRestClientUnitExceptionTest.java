package com.fundit.auth.infrastructure.portone;

import com.fundit.common.error.DependencyFailureException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PortOneRestClientUnitExceptionTest {

    private static final String API_SECRET = "test-api-secret";
    private static final String STORE_ID = "test-store-id";

    @Test
    void PortOne_호출이_실패하면_DependencyFailureException으로_감싼다() {
        // given
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.portone.io");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PortOneRestClient client = new PortOneRestClient(builder.build(), API_SECRET, STORE_ID);

        server.expect(requestTo("https://api.portone.io/identity-verifications/identity-verification-3?storeId=" + STORE_ID))
                .andRespond(withServerError());

        // when & then
        assertThatThrownBy(() -> client.fetchVerification("identity-verification-3"))
                .isInstanceOf(DependencyFailureException.class);
    }

    @Test
    void 생년월일_형식이_잘못되면_DependencyFailureException으로_감싼다() {
        // given
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.portone.io");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PortOneRestClient client = new PortOneRestClient(builder.build(), API_SECRET, STORE_ID);

        server.expect(requestTo("https://api.portone.io/identity-verifications/identity-verification-4?storeId=" + STORE_ID))
                .andRespond(withSuccess("""
                        {
                          "status": "VERIFIED",
                          "verifiedCustomer": {
                            "name": "홍길동",
                            "phoneNumber": "01012345678",
                            "birthDate": "not-a-date"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.fetchVerification("identity-verification-4"))
                .isInstanceOf(DependencyFailureException.class);
    }
}
