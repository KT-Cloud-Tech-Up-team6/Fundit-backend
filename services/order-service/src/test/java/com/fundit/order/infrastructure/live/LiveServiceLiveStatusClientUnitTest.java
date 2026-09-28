package com.fundit.order.infrastructure.live;

import com.fundit.order.application.live.LiveStatusClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * live-service 내부 API와의 요청/응답 왕복을 {@link MockRestServiceServer}로 검증한다
 * (payment-service {@code HttpShippingStatusClientUnitTest}와 동일 패턴).
 *
 * <p>여기서 보는 건 "세션 없음"이 전부 {@code empty}로 떨어지는지다. 실패(예외) 케이스는
 * {@code LiveServiceLiveStatusClientUnitExceptionTest}에 있다.
 */
class LiveServiceLiveStatusClientUnitTest {

    private static final String BASE_URL = "http://localhost:8086";
    private static final String INTERNAL_KEY = "test-internal-key";

    private MockRestServiceServer server;
    private LiveServiceLiveStatusClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new LiveServiceLiveStatusClient(builder.build(), INTERNAL_KEY);
    }

    @Test
    void 내부API키를_붙여_liveId로_방송_상태를_조회한다() {
        // given
        UUID liveId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        server.expect(requestTo(BASE_URL + "/internal/v1/lives/" + liveId + "/status"))
                .andExpect(method(GET))
                .andExpect(header("X-Internal-Api-Key", INTERNAL_KEY))
                .andRespond(withSuccess("""
                        {"liveId": "%s", "sessionId": 42, "status": "LIVE", "sellerId": "%s"}
                        """.formatted(liveId, sellerId), MediaType.APPLICATION_JSON));

        // when
        Optional<LiveStatusClient.LiveStatus> status = client.findByLiveId(liveId);

        // then
        assertThat(status).contains(new LiveStatusClient.LiveStatus(liveId, 42L, "LIVE", sellerId));
        server.verify();
    }

    @Test
    void projectId로_진행중인_방송을_조회한다() {
        // given
        UUID projectId = UUID.randomUUID();
        UUID liveId = UUID.randomUUID();
        server.expect(requestTo(BASE_URL + "/internal/v1/lives/by-project/" + projectId + "/active-status"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"liveId": "%s", "sessionId": 7, "status": "LIVE", "sellerId": null}
                        """.formatted(liveId), MediaType.APPLICATION_JSON));

        // when
        Optional<LiveStatusClient.LiveStatus> status = client.findActiveByProject(projectId);

        // then
        assertThat(status).map(LiveStatusClient.LiveStatus::sessionId).contains(7L);
        server.verify();
    }

    @Test
    void sessionId로_조회하면_종료된_방송도_상태_그대로_돌려준다() {
        // given — 게이트 판정은 호출부가 한다. 클라이언트는 상태를 가공하지 않는다.
        server.expect(requestTo(BASE_URL + "/internal/v1/lives/sessions/42/status"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"liveId": null, "sessionId": 42, "status": "ENDED", "sellerId": null}
                        """, MediaType.APPLICATION_JSON));

        // when
        Optional<LiveStatusClient.LiveStatus> status = client.findBySessionId(42L);

        // then
        assertThat(status).map(LiveStatusClient.LiveStatus::status).contains("ENDED");
        server.verify();
    }

    @Test
    void 응답이_전부_null이면_세션_없음으로_보고_empty를_돌려준다() {
        // given
        UUID projectId = UUID.randomUUID();
        server.expect(requestTo(BASE_URL + "/internal/v1/lives/by-project/" + projectId + "/active-status"))
                .andRespond(withSuccess("""
                        {"liveId": null, "sessionId": null, "status": null, "sellerId": null}
                        """, MediaType.APPLICATION_JSON));

        // when
        Optional<LiveStatusClient.LiveStatus> status = client.findActiveByProject(projectId);

        // then
        assertThat(status).isEmpty();
        server.verify();
    }

    @Test
    void 아직_200_null로_바뀌지_않은_엔드포인트의_404도_세션_없음으로_본다() {
        // given — 기존 /lives/{liveId}/status는 세션이 없으면 404를 던진다(L-2에서 정리 예정)
        UUID liveId = UUID.randomUUID();
        server.expect(requestTo(BASE_URL + "/internal/v1/lives/" + liveId + "/status"))
                .andRespond(withResourceNotFound());

        // when
        Optional<LiveStatusClient.LiveStatus> status = client.findByLiveId(liveId);

        // then
        assertThat(status).isEmpty();
        server.verify();
    }
}
