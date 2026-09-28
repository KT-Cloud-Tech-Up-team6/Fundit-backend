package com.fundit.order.infrastructure.live;

import com.fundit.common.error.DependencyFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * "세션 없음"이 아니라 "호출 실패"로 분류돼야 하는 케이스만 모았다 — 여기서 empty가 새어나가면
 * LIVE 쿠폰이 방송 여부를 확인하지 못한 채 발급된다.
 */
class LiveServiceLiveStatusClientUnitExceptionTest {

    private static final String BASE_URL = "http://localhost:8086";

    private MockRestServiceServer server;
    private LiveServiceLiveStatusClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new LiveServiceLiveStatusClient(builder.build(), "test-internal-key");
    }

    @Test
    void 서버오류면_DEPENDENCY_FAILURE로_감싼다() {
        // given
        server.expect(requestTo(BASE_URL + "/internal/v1/lives/sessions/42/status"))
                .andRespond(withServerError());

        // when & then
        assertThatThrownBy(() -> client.findBySessionId(42L))
                .isInstanceOf(DependencyFailureException.class);
    }

    @Test
    void 타임아웃이면_DEPENDENCY_FAILURE로_감싼다() {
        // given
        UUID projectId = UUID.randomUUID();
        server.expect(requestTo(BASE_URL + "/internal/v1/lives/by-project/" + projectId + "/active-status"))
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        // when & then
        assertThatThrownBy(() -> client.findActiveByProject(projectId))
                .isInstanceOf(DependencyFailureException.class);
    }

    @Test
    void 본문이_없으면_DEPENDENCY_FAILURE로_감싼다() {
        // given — 200인데 본문이 비어 오면 "세션 없음"인지 알 수 없다. empty로 내려보내지 않는다.
        UUID liveId = UUID.randomUUID();
        server.expect(requestTo(BASE_URL + "/internal/v1/lives/" + liveId + "/status"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> client.findByLiveId(liveId))
                .isInstanceOf(DependencyFailureException.class);
    }
}
