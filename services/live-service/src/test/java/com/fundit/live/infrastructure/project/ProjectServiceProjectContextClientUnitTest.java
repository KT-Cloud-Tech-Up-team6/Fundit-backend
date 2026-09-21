package com.fundit.live.infrastructure.project;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProjectServiceProjectContextClientUnitTest {

    private static final String BASE_URL = "http://project-service";

    private record Fixture(ProjectServiceProjectContextClient client, MockRestServiceServer server) {
    }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new ProjectServiceProjectContextClient(builder.build()), server);
    }

    @Test
    void 공개_상세응답에서_AI_컨텍스트_필드만_뽑는다() {
        // given — TEXT 블록만 소개문구로 쓰고 IMAGE는 버린다
        UUID projectId = UUID.randomUUID();
        Fixture f = fixture();
        f.server().expect(requestTo(BASE_URL + "/api/v1/projects/" + projectId))
                .andRespond(withSuccess("""
                        { "projectId": "%s", "title": "무선 미니 가습기",
                          "categoryMajor": "가전", "categoryMinor": "생활가전",
                          "introContent": [
                            { "type": "TEXT", "value": "타이머 최대 12시간" },
                            { "type": "IMAGE", "value": "https://x/img.png" }
                          ],
                          "fundingStatus": { "achievementRate": 42, "remainingDays": 5 } }
                        """.formatted(projectId), MediaType.APPLICATION_JSON));

        // when
        var found = f.client().find(projectId);

        // then
        assertThat(found).isPresent();
        var context = found.get();
        assertThat(context.title()).isEqualTo("무선 미니 가습기");
        assertThat(context.introTexts()).containsExactly("타이머 최대 12시간");
        assertThat(context.achievementRate()).isEqualTo(42);
        assertThat(context.remainingDays()).isEqualTo(5);
    }

    @Test
    void 없는_프로젝트는_예외가_아니라_empty다() {
        // given
        UUID projectId = UUID.randomUUID();
        Fixture f = fixture();
        f.server().expect(requestTo(BASE_URL + "/api/v1/projects/" + projectId))
                .andRespond(withResourceNotFound());

        // when & then
        assertThat(f.client().find(projectId)).isEmpty();
    }

    @Test
    void fundingStatus가_없으면_null로_채운다() {
        // given
        UUID projectId = UUID.randomUUID();
        Fixture f = fixture();
        f.server().expect(requestTo(BASE_URL + "/api/v1/projects/" + projectId))
                .andRespond(withSuccess("""
                        { "projectId": "%s", "title": "제목" }
                        """.formatted(projectId), MediaType.APPLICATION_JSON));

        // when
        var context = f.client().find(projectId).orElseThrow();

        // then
        assertThat(context.achievementRate()).isNull();
        assertThat(context.remainingDays()).isNull();
        assertThat(context.introTexts()).isEmpty();
    }
}
