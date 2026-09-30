package com.fundit.order.infrastructure.fulfillment;

import com.fundit.order.application.fulfillment.FulfillmentStatusClient.FulfillmentStatus;
import com.fundit.order.application.fulfillment.FulfillmentStatusClient.ProjectFulfillment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FulfillmentServiceFulfillmentStatusClientUnitTest {

    private static final String INTERNAL_KEY = "test-internal-key";
    private static final String BASE_URL = "http://localhost:8085";

    private MockRestServiceServer server;
    private FulfillmentServiceFulfillmentStatusClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FulfillmentServiceFulfillmentStatusClient(builder.build(), INTERNAL_KEY);
    }

    @Test
    void 단건_조회는_진행_기록_유무까지_역직렬화한다() {
        // given
        UUID orderId = UUID.randomUUID();
        server.expect(requestTo(BASE_URL + "/internal/fundings/" + orderId + "/fulfillment-status"))
                .andExpect(header("X-Internal-Api-Key", INTERNAL_KEY))
                .andRespond(withSuccess("""
                        {"isAlreadyShipped": false, "isDelayed": false, "deliveredAt": null,
                         "receiptConfirmedAt": null, "hasProgressRecord": true}
                        """, MediaType.APPLICATION_JSON));

        // when
        FulfillmentStatus status = client.fetch(orderId);

        // then
        assertThat(status.hasProgressRecord()).isTrue();
        assertThat(status.isAlreadyShipped()).isFalse();
        server.verify();
    }

    @Test
    void 프로젝트_배치_조회는_지연여부와_진행_기록을_함께_돌려준다() {
        // given
        UUID projectId = UUID.randomUUID();
        server.expect(requestTo(BASE_URL + "/internal/projects/shipping-delays?projectIds=" + projectId))
                .andExpect(header("X-Internal-Api-Key", INTERNAL_KEY))
                .andRespond(withSuccess("""
                        [{"projectId": "%s", "isDelayed": false, "hasProgressRecord": true}]
                        """.formatted(projectId), MediaType.APPLICATION_JSON));

        // when
        Map<UUID, ProjectFulfillment> result = client.fetchProjectStatuses(List.of(projectId));

        // then
        assertThat(result).containsEntry(projectId, new ProjectFulfillment(false, true));
        server.verify();
    }

    @Test
    void 프로젝트_배치_조회가_실패하면_지연_아님_기록_없음으로_degrade한다() {
        // given — 목록 자체는 내려가야 하므로 예외를 던지지 않는다.
        UUID projectId = UUID.randomUUID();
        server.expect(requestTo(BASE_URL + "/internal/projects/shipping-delays?projectIds=" + projectId))
                .andRespond(withServerError());

        // when
        Map<UUID, ProjectFulfillment> result = client.fetchProjectStatuses(List.of(projectId));

        // then
        assertThat(result).isEmpty();
        assertThat(ProjectFulfillment.NONE).isEqualTo(new ProjectFulfillment(false, false));
    }
}
