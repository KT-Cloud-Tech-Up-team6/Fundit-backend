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
    void 소개본문의_HTML_태그를_걷어내고_평문으로_돌려준다() {
        // given — project-service는 굵게/색상/정렬 서식을 HTML로 보존해서 내려준다.
        // 그대로 AI knowledge로 넘기면 근거와 답변 본문에 태그가 섞인다(#240).
        UUID projectId = UUID.randomUUID();
        Fixture f = fixture();
        f.server().expect(requestTo(BASE_URL + "/api/v1/projects/" + projectId))
                .andRespond(withSuccess("""
                        { "projectId": "%s", "title": "무선 미니 가습기",
                          "introContent": [
                            { "type": "TEXT", "value": "<p style=\\"text-align:center\\"><b>타이머</b> 최대 12시간</p><p>USB-C 충전</p>" },
                            { "type": "TEXT", "value": "소비전력 5W&nbsp;/&nbsp;물탱크 300ml &amp; 무소음" },
                            { "type": "TEXT", "value": "<p><br></p>" }
                          ] }
                        """.formatted(projectId), MediaType.APPLICATION_JSON));

        // when
        var context = f.client().find(projectId).orElseThrow();

        // then — 인라인 태그는 단어를 쪼개지 않게 그냥 지우고, 블록 태그는 문장 경계로 공백이 된다.
        // 엔티티는 복원하고, 태그만 있던 블록은 빈 청크가 되므로 아예 넘기지 않는다.
        assertThat(context.introTexts()).containsExactly(
                "타이머 최대 12시간 USB-C 충전",
                "소비전력 5W / 물탱크 300ml & 무소음");
    }

    @Test
    void 본문에_적힌_태그_문자는_지우지_않는다() {
        // given — 글쓴이가 "<b>"라고 적은 경우. 발행 측이 &lt;b&gt;로 이스케이프해서 내려준다.
        // 엔티티를 먼저 풀면 이게 진짜 태그가 되어 지워진다 — 쓴 글자가 사라진다.
        UUID projectId = UUID.randomUUID();
        Fixture f = fixture();
        f.server().expect(requestTo(BASE_URL + "/api/v1/projects/" + projectId))
                .andRespond(withSuccess("""
                        { "projectId": "%s", "title": "제목",
                          "introContent": [
                            { "type": "TEXT", "value": "<p>굵게는 &lt;b&gt; 태그로 씁니다</p>" }
                          ] }
                        """.formatted(projectId), MediaType.APPLICATION_JSON));

        // when
        var context = f.client().find(projectId).orElseThrow();

        // then
        assertThat(context.introTexts()).containsExactly("굵게는 <b> 태그로 씁니다");
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
