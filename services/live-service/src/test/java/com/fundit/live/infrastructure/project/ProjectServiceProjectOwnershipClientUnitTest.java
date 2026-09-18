package com.fundit.live.infrastructure.project;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProjectServiceProjectOwnershipClientUnitTest {

    private static final String BASE_URL = "http://project-service";

    private record Fixture(ProjectServiceProjectOwnershipClient client, MockRestServiceServer server) {
    }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new ProjectServiceProjectOwnershipClient(builder.build()), server);
    }

    @Test
    void 공개_상세응답의_seller_sellerId를_소유자로_읽는다() {
        // given — 내부 API는 Long id만 받아 UUID로는 호출할 수 없다. 공개 API를 쓴다.
        UUID projectId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        Fixture f = fixture();
        f.server().expect(requestTo(BASE_URL + "/api/v1/projects/" + projectId))
                .andRespond(withSuccess("""
                        { "projectId": "%s", "title": "무선 미니 가습기",
                          "seller": { "sellerId": "%s", "displayName": "메이커" } }
                        """.formatted(projectId, sellerId), MediaType.APPLICATION_JSON));

        // when
        Optional<UUID> found = f.client().findSellerId(projectId);

        // then
        assertThat(found).contains(sellerId);
        f.server().verify();
    }

    @Test
    void 없는_프로젝트는_예외가_아니라_empty다() {
        // given — 호출 측이 404로 바꾼다. 여기서 던지면 503(외부 연동 실패)로 뭉개진다.
        UUID projectId = UUID.randomUUID();
        Fixture f = fixture();
        f.server().expect(requestTo(BASE_URL + "/api/v1/projects/" + projectId))
                .andRespond(withResourceNotFound());

        // when & then
        assertThat(f.client().findSellerId(projectId)).isEmpty();
    }

    @Test
    void seller가_비어_있으면_empty다() {
        // given — 외부 응답을 신뢰하지 않고 필요한 필드의 존재를 확인한다(security.md S7)
        UUID projectId = UUID.randomUUID();
        Fixture f = fixture();
        f.server().expect(requestTo(BASE_URL + "/api/v1/projects/" + projectId))
                .andRespond(withSuccess("""
                        { "projectId": "%s", "title": "제목" }
                        """.formatted(projectId), MediaType.APPLICATION_JSON));

        // when & then
        assertThat(f.client().findSellerId(projectId)).isEmpty();
    }
}
