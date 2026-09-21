package com.fundit.live.infrastructure.ai;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.DependencyFailureException;
import com.fundit.live.application.ai.AiClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

class HttpAiClientUnitExceptionTest {

    private RestClient.Builder builder() {
        return AiClientConfig.builder("https://ai.fundit.internal", "test-token");
    }

    @Test
    void prepare_없이_댓글을_보내면_409를_그대로_알린다() {
        // given — NOT_PREPARED. 우리 쪽 배선 실수라 사용자에게 보일 값이 아니지만
        // CONFLICT로 구분해 로그에서 원인을 바로 알 수 있게 한다
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        HttpAiClient aiClient = new HttpAiClient(client, client);

        server.expect(requestTo("https://ai.fundit.internal/lives/live-1/comments"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CONFLICT));

        // when & then
        assertThatThrownBy(() -> aiClient.submitComments("live-1",
                List.of(new AiClient.CommentInput("c1", "질문", 0, UUID.randomUUID()))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.CONFLICT);
    }

    @Test
    void 없는_미답변_질문을_조회하면_404다() {
        // given
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        HttpAiClient aiClient = new HttpAiClient(client, client);

        server.expect(requestTo("https://ai.fundit.internal/lives/live-1/unanswered/fq_9999"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> aiClient.unansweredDetail("live-1", "fq_9999"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 인증_실패는_DependencyFailureException으로_감싼다() {
        // given — 401은 우리 쪽 API_TOKEN 설정 문제라 사용자 응답이 아니라 장애로 다룬다
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        HttpAiClient aiClient = new HttpAiClient(client, client);

        server.expect(requestTo("https://ai.fundit.internal/lives/live-1/faq?top_n=10"))
                .andRespond(withUnauthorizedRequest());

        // when & then
        assertThatThrownBy(() -> aiClient.faq("live-1", 10))
                .isInstanceOf(DependencyFailureException.class);
    }
}
