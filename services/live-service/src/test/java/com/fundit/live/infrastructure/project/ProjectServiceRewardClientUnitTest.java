package com.fundit.live.infrastructure.project;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProjectServiceRewardClientUnitTest {

    private static final String BASE_URL = "http://project-service";

    private record Fixture(ProjectServiceRewardClient client, MockRestServiceServer server) {
    }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new ProjectServiceRewardClient(builder.build()), server);
    }

    @Test
    void 리워드_목록을_AI_입력_모양으로_옮긴다() {
        // given
        UUID projectId = UUID.randomUUID();
        Fixture f = fixture();
        f.server().expect(requestTo(BASE_URL + "/api/v1/projects/" + projectId + "/rewards"))
                .andRespond(withSuccess("""
                        [ { "rewardDisplayCode": "R0000001", "name": "얼리버드 패키지",
                            "description": "설명", "price": 39000, "isLimited": true,
                            "remainingStock": 37, "isEarlyBird": true,
                            "options": [ { "groupName": "색상", "values": [ { "value": "화이트" } ] } ] } ]
                        """, MediaType.APPLICATION_JSON));

        // when
        var rewards = f.client().findRewards(projectId);

        // then
        assertThat(rewards).hasSize(1);
        var reward = rewards.getFirst();
        assertThat(reward.rewardDisplayCode()).isEqualTo("R0000001");
        assertThat(reward.quantity()).isEqualTo(37);
        assertThat(reward.optionGroups().getFirst().values()).containsExactly("화이트");
    }

    @Test
    void 없는_프로젝트는_예외가_아니라_빈_목록이다() {
        // given
        UUID projectId = UUID.randomUUID();
        Fixture f = fixture();
        f.server().expect(requestTo(BASE_URL + "/api/v1/projects/" + projectId + "/rewards"))
                .andRespond(withResourceNotFound());

        // when & then
        assertThat(f.client().findRewards(projectId)).isEqualTo(List.of());
    }
}
