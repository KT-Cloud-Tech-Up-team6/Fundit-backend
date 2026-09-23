package com.fundit.live.application.question;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fundit.common.error.DependencyFailureException;
import com.fundit.live.application.ai.AiClient;
import com.fundit.live.application.ivs.IvsClient;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import com.fundit.live.infrastructure.persistence.question.LiveQuestionSummaryJpaEntity;
import com.fundit.live.infrastructure.persistence.question.LiveQuestionSummaryJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiAnswerServiceUnitTest {

    @Mock private LiveQuestionSummaryJpaRepository summaryRepository;
    @Mock private LiveSessionRepository sessionRepository;
    @Mock private AiClient aiClient;
    @Mock private IvsClient ivsClient;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private AiAnswerService aiAnswerService;

    private final UUID sellerId = UUID.randomUUID();
    private final UUID liveId = UUID.randomUUID();
    private final UUID questionId = UUID.randomUUID();

    private LiveQuestionSummaryJpaEntity summary() {
        return LiveQuestionSummaryJpaEntity.builder()
                .id(5L).publicId(questionId).sessionId(1L).aiQuestionId("fq_0002")
                .summaryText("사이즈가 어떻게 되나요?").relatedQuestionCount(12).answered(false).build();
    }

    private void givenOwnedAndSummary(LiveQuestionSummaryJpaEntity s) {
        given(sessionRepository.findOwned(liveId, sellerId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
        given(summaryRepository.findByPublicIdAndSessionId(questionId, 1L)).willReturn(Optional.of(s));
    }

    @Test
    void GENERATE는_초안만_만들고_답변으로_기록하지_않는다() {
        // given — 자동 게시가 아니다. 판매자 승인 후 전송이 협의 결정이다.
        LiveQuestionSummaryJpaEntity s = summary();
        givenOwnedAndSummary(s);
        given(aiClient.unansweredDetail(anyString(), eq("fq_0002"))).willReturn(
                new AiClient.UnansweredDetail("사이즈가 어떻게 되나요?", 12,
                        new AiClient.Reference(List.of(), List.of()),
                        "500ml/700ml 두 가지입니다.", null));

        // when
        AiClient.UnansweredDetail draft = aiAnswerService.draft(sellerId, liveId, questionId);

        // then
        assertThat(draft.draft()).isNotBlank();
        assertThat(s.isAnswered()).isFalse();
        assertThat(s.getAnswerText()).isNull();
    }

    @Test
    void SEND해야_AI에_등록되고_저장된다() {
        // given — 채팅 스트림은 지나가면 끝이라 저장하지 않으면 Q&A 버튼이 보여줄 게 없다
        LiveQuestionSummaryJpaEntity s = summary();
        givenOwnedAndSummary(s);
        given(aiClient.registerSellerAnswer(anyString(), eq("fq_0002"), anyString()))
                .willReturn(new AiClient.SellerAnswerResult(true));

        // when
        aiAnswerService.send(sellerId, liveId, questionId, "500ml/700ml 두 가지입니다.");

        // then
        verify(aiClient).registerSellerAnswer(liveId.toString(), "fq_0002", "500ml/700ml 두 가지입니다.");
        assertThat(s.isAnswered()).isTrue();
        assertThat(s.getAnswerText()).isEqualTo("500ml/700ml 두 가지입니다.");
        assertThat(s.getAnsweredAt()).isNotNull();
    }

    @Test
    void SEND하면_게시_이벤트만_발행하고_직접_보내지_않는다() {
        // given — 게시는 커밋 뒤 별도 스레드(onSellerAnswerSent)에서 한다
        LiveQuestionSummaryJpaEntity s = summary();
        givenLiveWithRoomAndSummary(s);
        given(aiClient.registerSellerAnswer(anyString(), eq("fq_0002"), anyString()))
                .willReturn(new AiClient.SellerAnswerResult(true));

        // when
        aiAnswerService.send(sellerId, liveId, questionId, "500ml/700ml 두 가지입니다.");

        // then
        verify(eventPublisher).publishEvent(new AiAnswerService.SellerAnswerSent(
                "arn:room", liveId, questionId, "500ml/700ml 두 가지입니다."));
        verify(ivsClient, never()).sendChatEvent(anyString(), anyString(), anyMap());
    }

    @Test
    void 채팅방이_없으면_게시_이벤트를_발행하지_않는다() {
        // given — 방송을 시작한 적 없는 세션은 채팅방 ARN이 없다
        LiveQuestionSummaryJpaEntity s = summary();
        givenOwnedAndSummary(s);
        given(aiClient.registerSellerAnswer(anyString(), eq("fq_0002"), anyString()))
                .willReturn(new AiClient.SellerAnswerResult(true));

        // when
        aiAnswerService.send(sellerId, liveId, questionId, "500ml/700ml 두 가지입니다.");

        // then
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void 게시_이벤트를_받으면_채팅방에_보낸다() {
        // given
        AiAnswerService.SellerAnswerSent event =
                new AiAnswerService.SellerAnswerSent("arn:room", liveId, questionId, "500ml/700ml 두 가지입니다.");

        // when
        aiAnswerService.onSellerAnswerSent(event);

        // then
        verify(ivsClient).sendChatEvent("arn:room", AiAnswerService.CHAT_EVENT_NAME,
                Map.of("questionId", questionId.toString(), "answer", "500ml/700ml 두 가지입니다."));
    }

    @Test
    void 채팅_게시에_실패해도_예외를_밖으로_던지지_않는다() {
        // given — 게시는 부가 동작이다. 답변은 이미 커밋됐고, 여기서 던지면 로그 없이 비동기 스레드에서 사라진다.
        willThrow(new DependencyFailureException(new RuntimeException("ivs down")))
                .given(ivsClient).sendChatEvent(anyString(), anyString(), anyMap());
        AiAnswerService.SellerAnswerSent event =
                new AiAnswerService.SellerAnswerSent("arn:room", liveId, questionId, "500ml/700ml 두 가지입니다.");

        // when & then
        assertThatCode(() -> aiAnswerService.onSellerAnswerSent(event)).doesNotThrowAnyException();
    }

    @Test
    void 속성_한도를_넘는_긴_답변은_questionId만_보낸다() {
        // given — IVS SendEvent attributes는 합계 4KB 상한이다. 넘기면 게시 자체가 거부된다.
        String longAnswer = "가".repeat(AiAnswerService.CHAT_EVENT_ATTRIBUTES_MAX_BYTES);

        // when
        Map<String, String> attributes = AiAnswerService.chatEventAttributes(questionId, longAnswer);

        // then — FE는 answered-questions에서 이 questionId로 답변을 조회한다
        assertThat(attributes).containsOnlyKeys("questionId");
        assertThat(attributes.get("questionId")).isEqualTo(questionId.toString());
    }

    @Test
    void 한도_이내_답변은_답변까지_같이_보낸다() {
        // given
        String answer = "500ml입니다";

        // when
        Map<String, String> attributes = AiAnswerService.chatEventAttributes(questionId, answer);

        // then
        assertThat(attributes).containsEntry("answer", answer)
                .containsEntry("questionId", questionId.toString());
    }

    private void givenLiveWithRoomAndSummary(LiveQuestionSummaryJpaEntity s) {
        given(sessionRepository.findOwned(liveId, sellerId)).willReturn(Optional.of(
                LiveSession.builder().id(1L).publicId(liveId).ivsChatRoomArn("arn:room").build()));
        given(summaryRepository.findByPublicIdAndSessionId(questionId, 1L)).willReturn(Optional.of(s));
    }

    @Test
    void AI_Live_Knowledge_등록에_실패해도_판매자_답변은_그대로_저장된다() {
        // given — AI 재사용 등록이 실패해도 화면에 보여줄 판매자 답변 자체는 남아야 한다.
        // 재사용 등록 실패 자체는 로그로만 남긴다(AI팀 요청 검토 중 발견한 갭).
        LiveQuestionSummaryJpaEntity s = summary();
        givenOwnedAndSummary(s);
        given(aiClient.registerSellerAnswer(anyString(), eq("fq_0002"), anyString()))
                .willReturn(new AiClient.SellerAnswerResult(false));

        Logger logger = (Logger) LoggerFactory.getLogger(AiAnswerService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            // when
            aiAnswerService.send(sellerId, liveId, questionId, "500ml/700ml 두 가지입니다.");

            // then — 답변은 저장되고, 실패는 로그로 남는다
            assertThat(s.isAnswered()).isTrue();
            assertThat(s.getAnswerText()).isEqualTo("500ml/700ml 두 가지입니다.");
            assertThat(appender.list).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).contains("AI Live Knowledge 등록 실패");
            });
        } finally {
            logger.detachAppender(appender);
        }
    }
}
