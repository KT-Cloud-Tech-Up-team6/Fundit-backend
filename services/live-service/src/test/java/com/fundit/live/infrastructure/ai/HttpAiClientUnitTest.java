package com.fundit.live.infrastructure.ai;

import com.fundit.live.application.ai.AiClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * snake_case 변환·필드 매핑이 실계약 예시 그대로 왕복하는지 확인한다 — 여기서 깨지면
 * 개발 중엔 안 보이다가 실제 AI 서버에 붙였을 때만 드러난다.
 */
class HttpAiClientUnitTest {

    private RestClient.Builder builder() {
        return AiClientConfig.builder("https://ai.fundit.internal", "test-token");
    }

    @Test
    void prepare는_snake_case로_본문을_보낸다() {
        // given
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        HttpAiClient aiClient = new HttpAiClient(client, client);

        server.expect(requestTo("https://ai.fundit.internal/lives/live-1/prepare"))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"product_name\"")))
                .andRespond(withSuccess("""
                        {"status": "ready", "product_name": "에어쿡 프로", "chunks": 5, "reward_chunks": 4}
                        """, MediaType.APPLICATION_JSON));

        // when
        aiClient.prepare("live-1", new AiClient.PrepareRequest("에어쿡 프로", "가전", "주방가전",
                "F0000042", "018f2c1a-3b4e-7a12-9c9d-0a1b2c3d4e5f", List.of(), List.of()));

        // then
        server.verify();
    }

    @Test
    void submitComments는_qid가_아니라_questionId_필드로_역직렬화한다() {
        // given — A-3(comments) 응답은 question_id, FAQ 응답은 qid로 서로 다르다
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        HttpAiClient aiClient = new HttpAiClient(client, client);

        server.expect(requestTo("https://ai.fundit.internal/lives/live-1/comments"))
                .andRespond(withSuccess("""
                        {
                          "status": "ok",
                          "questions": [
                            { "question_id": "q_0001", "comment_id": "c_1041", "text": "흡입력 얼마나 돼요?",
                              "handled_by": "PRODUCT", "category": "제품 성능 및 사양", "at_ms": 331200,
                              "answer": { "text": "최대 흡입력은 20,000Pa입니다.", "grounding": "GROUNDED",
                                          "strict": false, "source": "kb_p_005" } }
                          ],
                          "ignored": [ { "comment_id": "c_1044", "reason": "SMALLTALK" } ]
                        }
                        """, MediaType.APPLICATION_JSON));

        // when
        AiClient.CommentBatchResult result = aiClient.submitComments("live-1",
                List.of(new AiClient.CommentInput("c_1041", "흡입력 얼마나 돼요?", 331200, UUID.randomUUID())));

        // then
        assertThat(result.questions()).hasSize(1);
        assertThat(result.questions().getFirst().questionId()).isEqualTo("q_0001");
        assertThat(result.questions().getFirst().handledBy()).isEqualTo(AiClient.HandledBy.PRODUCT);
        assertThat(result.questions().getFirst().answer().grounding()).isEqualTo(AiClient.Grounding.GROUNDED);
        assertThat(result.ignored()).hasSize(1);
        // 응답에 errors 필드가 없어도 null이 아니라 빈 리스트다 — 발송기가 그대로 순회한다
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void faq는_qid를_클러스터_식별자로_읽는다() {
        // given
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        HttpAiClient aiClient = new HttpAiClient(client, client);

        server.expect(requestTo("https://ai.fundit.internal/lives/live-1/faq?top_n=10"))
                .andRespond(withSuccess("""
                        { "window_sec": 180,
                          "qna": [ { "qid": "fq_0002", "representative_text": "타이머 기능 돼요?", "count": 4,
                                     "category": "앱·원격제어", "answered_by": "SELLER",
                                     "answer": "네, 최대 12시간 예약 타이머가 있습니다.", "promoted": true } ] }
                        """, MediaType.APPLICATION_JSON));

        // when
        AiClient.FaqResult result = aiClient.faq("live-1", 10);

        // then
        assertThat(result.qna()).hasSize(1);
        assertThat(result.qna().getFirst().qid()).isEqualTo("fq_0002");
        assertThat(result.qna().getFirst().answeredBy()).isEqualTo(AiClient.AnsweredBy.SELLER);
        assertThat(result.qna().getFirst().promoted()).isTrue();
    }

    @Test
    void 큐시트_요청은_계약_미정이라_지원하지_않는다() {
        // given & when & then — HttpAiClient가 다룰 대상이 아니다
        HttpAiClient aiClient = new HttpAiClient(builder().build(), builder().build());
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        aiClient.requestCueSheet("live-1", new AiClient.CueSheetRequest(
                                "SCENARIO", 580, false, List.of(), null, List.of())))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
