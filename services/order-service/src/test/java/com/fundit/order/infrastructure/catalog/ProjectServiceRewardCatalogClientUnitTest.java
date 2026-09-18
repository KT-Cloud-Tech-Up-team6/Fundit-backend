package com.fundit.order.infrastructure.catalog;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.order.application.catalog.RewardCatalogClient.RewardSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProjectServiceRewardCatalogClientUnitTest {

    private static final UUID PROJECT_ID = UUID.fromString("018f2c1a-3b4e-7a12-9c9d-0a1b2c3d4e5f");

    private MockRestServiceServer server;
    private ProjectServiceRewardCatalogClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8083");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ProjectServiceRewardCatalogClient(builder.build());
    }

    @Test
    void 리워드와_옵션을_스냅샷으로_변환한다() {
        // given
        server.expect(requestTo("http://localhost:8083/api/v1/projects/" + PROJECT_ID + "/rewards"))
                .andRespond(withSuccess("""
                        [
                          {"rewardId": 1, "rewardDisplayCode": "R1", "name": "얼리버드 패키지", "price": 10000,
                           "isEarlyBird": true, "isLimited": true, "remainingStock": 5, "soldOut": false,
                           "options": [
                             {"groupId": 10, "groupName": "색상", "values": [{"valueId": 100, "value": "블랙"}]}
                           ]}
                        ]
                        """, MediaType.APPLICATION_JSON));

        // when
        List<RewardSnapshot> result = client.getRewards(PROJECT_ID);

        // then
        assertThat(result).singleElement().satisfies(reward -> {
            assertThat(reward.rewardId()).isEqualTo(1L);
            assertThat(reward.name()).isEqualTo("얼리버드 패키지");
            assertThat(reward.price()).isEqualTo(10_000L);
            assertThat(reward.isLimited()).isTrue();
            assertThat(reward.optionGroups()).singleElement().satisfies(group -> {
                assertThat(group.groupName()).isEqualTo("색상");
                assertThat(group.values()).singleElement().satisfies(value ->
                        assertThat(value.value()).isEqualTo("블랙"));
            });
        });
        server.verify();
    }

    @Test
    void 응답이_없으면_빈_목록을_반환한다() {
        // given
        server.expect(requestTo("http://localhost:8083/api/v1/projects/" + PROJECT_ID + "/rewards"))
                .andRespond(withSuccess());

        // when
        List<RewardSnapshot> result = client.getRewards(PROJECT_ID);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void 호출이_실패하면_DEPENDENCY_FAILURE로_감싼다() {
        // given
        server.expect(requestTo("http://localhost:8083/api/v1/projects/" + PROJECT_ID + "/rewards"))
                .andRespond(withServerError());

        // when & then
        assertThatThrownBy(() -> client.getRewards(PROJECT_ID))
                .isInstanceOf(DependencyFailureException.class);
    }
}
