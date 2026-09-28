package com.fundit.search.infrastructure.live;

import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.DependencyFailureException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

/**
 * live-service 실패는 전부 {@link DependencyFailureException}(503)으로 바꾼다 —
 * RestClient 예외가 그대로 올라가면 GlobalExceptionHandler가 500으로 응답한다.
 */
class LiveServiceLiveCardClientUnitExceptionTest {

    private static final String BASE_URL = "http://live-service";

    private record Fixture(LiveServiceLiveCardClient client, MockRestServiceServer server) {
    }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new LiveServiceLiveCardClient(builder.build()), server);
    }

    @Test
    void 목록_조회가_실패하면_DependencyFailureException이다() {
        // given
        Fixture f = fixture();
        f.server().expect(requestTo(BASE_URL + "/api/v1/lives?page=0&size=20")).andRespond(withServerError());

        // when & then
        assertThatThrownBy(() -> f.client().findPublic(PageRequest.of(0, 20)))
                .isInstanceOf(DependencyFailureException.class)
                .satisfies(e -> assertThat(((DependencyFailureException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.DEPENDENCY_FAILURE));
    }

    @Test
    void 배너_조회가_실패하면_DependencyFailureException이다() {
        // given
        Fixture f = fixture();
        f.server().expect(requestTo(BASE_URL + "/api/v1/lives/banner")).andRespond(withServerError());

        // when & then — 이 예외를 홈 컨트롤러가 잡아 빈 목록으로 떨어뜨린다
        assertThatThrownBy(f.client()::findBanner).isInstanceOf(DependencyFailureException.class);
    }
}
