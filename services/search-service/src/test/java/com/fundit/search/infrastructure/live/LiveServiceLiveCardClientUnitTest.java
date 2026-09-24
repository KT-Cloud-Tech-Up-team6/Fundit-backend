package com.fundit.search.infrastructure.live;

import com.fundit.search.application.live.LiveCardClient.LiveCard;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LiveServiceLiveCardClientUnitTest {

    private static final String BASE_URL = "http://live-service";

    private record Fixture(LiveServiceLiveCardClient client, MockRestServiceServer server) {
    }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new LiveServiceLiveCardClient(builder.build()), server);
    }

    @Test
    void 공개_목록을_카드로_파싱한다() {
        // given
        UUID liveId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Fixture f = fixture();
        f.server().expect(requestTo(BASE_URL + "/api/v1/lives?page=0&size=20"))
                .andRespond(withSuccess("""
                        { "content": [
                            { "liveId": "%s", "introText": "캠핑 의자 라이브", "status": "SCHEDULED",
                              "projectId": "%s", "thumbnailUrl": "https://cdn/thumb.png",
                              "scheduledStartAt": "2026-09-25T11:00:00Z", "likeCount": 3,
                              "createdAt": "2026-09-20T09:00:00Z" }
                          ],
                          "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "hasNext": false }
                        """.formatted(liveId, projectId), MediaType.APPLICATION_JSON));

        // when
        Page<LiveCard> page = f.client().findPublic(PageRequest.of(0, 20));

        // then
        assertThat(page.getTotalElements()).isEqualTo(1);
        LiveCard card = page.getContent().getFirst();
        assertThat(card.liveId()).isEqualTo(liveId);
        assertThat(card.projectId()).isEqualTo(projectId);
        assertThat(card.introText()).isEqualTo("캠핑 의자 라이브");
        assertThat(card.status()).isEqualTo("SCHEDULED");
        assertThat(card.likeCount()).isEqualTo(3);
        assertThat(card.scheduledStartAt()).isEqualTo(Instant.parse("2026-09-25T11:00:00Z"));
        // live-service는 sort=viewerCount일 때만 채우므로 이 경로에서는 항상 비어 있다
        assertThat(card.viewerCount()).isNull();
    }

    @Test
    void 마지막_페이지에서도_전체_건수는_live_service가_센_값을_쓴다() {
        // given — content.size()로 재계산하면 전체 건수가 페이지 크기로 줄어든다
        Fixture f = fixture();
        f.server().expect(requestTo(BASE_URL + "/api/v1/lives?page=2&size=20"))
                .andRespond(withSuccess("""
                        { "content": [], "page": 2, "size": 20, "totalElements": 41,
                          "totalPages": 3, "hasNext": false }
                        """, MediaType.APPLICATION_JSON));

        // when
        Page<LiveCard> page = f.client().findPublic(PageRequest.of(2, 20));

        // then
        assertThat(page.getTotalElements()).isEqualTo(41);
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void 배너는_배열_응답을_그대로_내려준다() {
        // given — 목록과 달리 페이지 래퍼가 없는 순수 배열이다
        UUID liveId = UUID.randomUUID();
        Fixture f = fixture();
        f.server().expect(requestTo(BASE_URL + "/api/v1/lives/banner"))
                .andRespond(withSuccess("""
                        [ { "liveId": "%s", "introText": "방송 중", "status": "LIVE",
                            "projectId": "%s", "likeCount": 12, "createdAt": "2026-09-24T01:00:00Z" } ]
                        """.formatted(liveId, UUID.randomUUID()), MediaType.APPLICATION_JSON));

        // when
        List<LiveCard> banner = f.client().findBanner();

        // then
        assertThat(banner).hasSize(1);
        assertThat(banner.getFirst().liveId()).isEqualTo(liveId);
        assertThat(banner.getFirst().status()).isEqualTo("LIVE");
        assertThat(banner.getFirst().thumbnailUrl()).isNull();
    }

    @Test
    void 방송_중인_LIVE가_없으면_빈_목록이다() {
        // given
        Fixture f = fixture();
        f.server().expect(requestTo(BASE_URL + "/api/v1/lives/banner"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // when & then — 에러가 아니라 "진행 중 LIVE 없음"이다
        assertThat(f.client().findBanner()).isEmpty();
    }
}
