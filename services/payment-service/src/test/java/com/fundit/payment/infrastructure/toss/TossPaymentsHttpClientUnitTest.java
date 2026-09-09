package com.fundit.payment.infrastructure.toss;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.payment.application.payment.TossApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TossPaymentsHttpClientUnitTest {

    private MockRestServiceServer server;
    private TossPaymentsHttpClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TossPaymentsHttpClient(builder.build());
    }

    @Test
    void 승인에_성공하면_토스_응답을_매핑한다() {
        server.expect(requestTo("http://localhost/v1/payments/confirm"))
                .andExpect(method(POST))
                .andRespond(withSuccess("""
                        {
                          "paymentKey": "pay_1",
                          "orderId": "fundit-1",
                          "status": "DONE",
                          "method": "간편결제",
                          "easyPay": {"provider": "KAKAOPAY"},
                          "approvedAt": "2026-09-08T14:23:11+09:00",
                          "totalAmount": 89000,
                          "secret": "secret_1"
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = client.confirm("pay_1", "fundit-1", 89_000L);

        assertThat(result.paymentKey()).isEqualTo("pay_1");
        assertThat(result.secret()).isEqualTo("secret_1");
        assertThat(result.method()).isEqualTo("간편결제");
        assertThat(result.easyPayProvider()).isEqualTo("KAKAOPAY");
        assertThat(result.totalAmount()).isEqualTo(89_000L);
        assertThat(result.approvedAt()).isNotNull();
        server.verify();
    }

    @Test
    void 취소에_성공하면_마지막_취소내역을_반환한다() {
        server.expect(requestTo("http://localhost/v1/payments/pay_1/cancel"))
                .andExpect(method(POST))
                .andRespond(withSuccess("""
                        {
                          "paymentKey": "pay_1",
                          "orderId": "fundit-1",
                          "method": "카드",
                          "approvedAt": "2026-09-08T14:23:11+09:00",
                          "totalAmount": 89000,
                          "secret": "secret_1",
                          "cancels": [
                            {"transactionKey": "tx_old", "cancelAmount": 1000, "canceledAt": "2026-09-08T15:00:00+09:00"},
                            {"transactionKey": "tx_new", "cancelAmount": 88000, "canceledAt": "2026-09-08T16:00:00+09:00"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = client.cancel("pay_1", 88_000L, "하자환불 승인");

        assertThat(result.transactionKey()).isEqualTo("tx_new");
        assertThat(result.cancelAmount()).isEqualTo(88_000L);
        assertThat(result.canceledAt()).isNotNull();
        server.verify();
    }

    @Test
    void 토스_에러_본문이_있으면_TossApiException으로_변환한다() {
        server.expect(requestTo("http://localhost/v1/payments/confirm"))
                .andExpect(method(POST))
                .andRespond(withBadRequest().body("""
                        {"code":"REJECT_CARD_COMPANY","message":"카드사 거절"}
                        """).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.confirm("pay_1", "fundit-1", 89_000L))
                .isInstanceOf(TossApiException.class)
                .satisfies(e -> {
                    TossApiException ex = (TossApiException) e;
                    assertThat(ex.getTossErrorCode()).isEqualTo("REJECT_CARD_COMPANY");
                    assertThat(ex.getTossMessage()).isEqualTo("카드사 거절");
                });
    }

    @Test
    void 취소_내역이_없으면_의존성_실패다() {
        server.expect(requestTo("http://localhost/v1/payments/pay_1/cancel"))
                .andExpect(method(POST))
                .andRespond(withSuccess("""
                        {"paymentKey":"pay_1","orderId":"fundit-1","method":"카드","totalAmount":89000,"secret":"s","cancels":[]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.cancel("pay_1", 89_000L, "취소"))
                .isInstanceOf(DependencyFailureException.class);
    }

    @Test
    void 서버_오류_본문_파싱에_실패하면_UNKNOWN_ERROR다() {
        server.expect(requestTo("http://localhost/v1/payments/confirm"))
                .andExpect(method(POST))
                .andRespond(withServerError().body("not-json").contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client.confirm("pay_1", "fundit-1", 89_000L))
                .isInstanceOf(TossApiException.class)
                .satisfies(e -> assertThat(((TossApiException) e).getTossErrorCode()).isEqualTo("UNKNOWN_ERROR"));
    }
}
