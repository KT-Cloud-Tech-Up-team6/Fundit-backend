package com.fundit.payment.infrastructure.settlement;

import com.fundit.common.error.DependencyFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * order-service 내부 API(PAYMENT-009/012)와의 요청/응답 왕복을 {@link MockRestServiceServer}로
 * 검증한다 — {@code HttpShippingStatusClientUnitTest}와 동일 패턴.
 */
class HttpOrderSettlementAggregateClientUnitTest {

    private static final String INTERNAL_KEY = "test-internal-key";

    private MockRestServiceServer server;
    private HttpOrderSettlementAggregateClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8084");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpOrderSettlementAggregateClient(builder.build(), INTERNAL_KEY);
    }

    @Test
    void 내부API키를_붙여_라인아이템을_조회한다() {
        server.expect(requestTo("http://localhost:8084/internal/fundings/1024/settlement-aggregate"))
                .andExpect(method(GET))
                .andExpect(header("X-Internal-Api-Key", INTERNAL_KEY))
                .andRespond(withSuccess("""
                        {
                          "lineItems": [
                            {"rewardId": 1, "rewardName": "얼리버드 패키지", "optionName": "색상: 화이트", "quantity": 2, "amount": 20000}
                          ],
                          "makerCouponDeductionAmount": 3000
                        }
                        """, MediaType.APPLICATION_JSON));

        var lineItems = client.fetchLineItems(1024L);

        assertThat(lineItems).hasSize(1);
        assertThat(lineItems.get(0).rewardId()).isEqualTo(1L);
        assertThat(lineItems.get(0).rewardName()).isEqualTo("얼리버드 패키지");
        assertThat(lineItems.get(0).optionName()).isEqualTo("색상: 화이트");
        assertThat(lineItems.get(0).quantity()).isEqualTo(2);
        assertThat(lineItems.get(0).amount()).isEqualTo(20000L);
        server.verify();
    }

    @Test
    void 내부API키를_붙여_메이커_쿠폰_차감액을_조회한다() {
        server.expect(requestTo("http://localhost:8084/internal/fundings/1024/settlement-aggregate"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"lineItems": [], "makerCouponDeductionAmount": 3000}
                        """, MediaType.APPLICATION_JSON));

        long amount = client.fetchMakerCouponDeductionAmount(1024L);

        assertThat(amount).isEqualTo(3000L);
        server.verify();
    }

    @Test
    void 호출이_실패하면_DEPENDENCY_FAILURE로_감싼다() {
        server.expect(requestTo("http://localhost:8084/internal/fundings/1024/settlement-aggregate"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchLineItems(1024L))
                .isInstanceOf(DependencyFailureException.class);
    }
}
