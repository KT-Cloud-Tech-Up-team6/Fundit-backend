package com.fundit.live.application.question;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.application.ai.AiClient;
import com.fundit.live.application.ivs.IvsClient;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import com.fundit.live.infrastructure.persistence.question.LiveQuestionSummaryJpaEntity;
import com.fundit.live.infrastructure.persistence.question.LiveQuestionSummaryJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 미답변 질문 판매자 답변(요구사항정의서 6.4.4.5·6.4.4.6). AI가 근거를 못 찾은
 * ({@code handledBy=UNANSWERABLE}) 질문만 이 흐름을 탄다 — 근거를 찾은 질문은
 * {@code submitComments} 응답으로 이미 즉시 답변되어 있다({@code ChatCommentBatchSender}).
 *
 * <p><b>생성과 등록이 분리돼 있다.</b> {@code draft}는 AI가 만든 초안 미리보기일 뿐 아무것도
 * 기록하지 않는다 — 판매자가 확인·수정한 최종 문구만 {@link #send}로 등록된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnswerService {

    private final LiveQuestionSummaryJpaRepository summaryRepository;
    private final LiveSessionRepository sessionRepository;
    private final AiClient aiClient;
    private final IvsClient ivsClient;
    private final ApplicationEventPublisher eventPublisher;

    static final String CHAT_EVENT_NAME = "seller-answer";
    static final int CHAT_EVENT_ATTRIBUTES_MAX_BYTES = IvsClient.CHAT_EVENT_ATTRIBUTES_MAX_BYTES;

    /** 답변 완료 처리 때 남기는 고정 문구(PM "(a) 고정 문구"). LIVE 체크·Q&A 목록에 답변으로 보인다. */
    static final String MARK_DONE_ANSWER = "방송 중 답변 완료";

    /** 초안 미리보기. 이 호출은 아무것도 기록하지 않는다. */
    @Transactional(readOnly = true)
    public AiClient.UnansweredDetail draft(UUID sellerId, UUID liveId, UUID questionId) {
        LiveQuestionSummaryJpaEntity summary = loadSummaryOf(loadOwned(sellerId, liveId), questionId);
        return aiClient.unansweredDetail(liveId.toString(), summary.getAiQuestionId());
    }

    /**
     * "방송 중 말로 답했다" 표시. 고정 문구({@value #MARK_DONE_ANSWER})를 로컬에만 기록한다.
     *
     * <p><b>코파일럿 {@code registerSellerAnswer}를 부르지 않는다.</b> 등록된 답변은 Live Knowledge에 들어가
     * 같은·유사 질문에 {@code SELLER_CONFIRMED}로 재사용된다 — 이 문구가 시청자 답변으로 나간다(AI 회신,
     * 등록을 끄는 플래그 없음). 같은 이유로 채팅에도 게시하지 않는다.
     *
     * <p>이미 답변된 질문은 덮지 않는다 — 판매자가 실제로 쓴 답변이 고정 문구로 바뀌면 안 된다.
     */
    @Transactional
    public LiveQuestionSummaryJpaEntity markDone(UUID sellerId, UUID liveId, UUID questionId) {
        LiveQuestionSummaryJpaEntity summary = loadSummaryOf(loadOwned(sellerId, liveId), questionId);
        if (!summary.isAnswered()) {
            summary.recordAnswer(MARK_DONE_ANSWER, Instant.now());
        }
        return summary;
    }

    /**
     * 판매자가 확인·수정한 최종 문구를 AI에 등록하고 로컬에도 <b>기록</b>한다.
     *
     * <p>AI 등록이 성공해야 로컬에 남긴다 — AI 쪽이 실패했는데 로컬만 "답변 완료"로 표시되면
     * 다음 유사 질문이 다시 미답변으로 잡히는데 화면은 이미 답변된 것으로 보여준다.
     *
     * <p>커밋 뒤 별도 스레드에서 IVS Chat {@code SendEvent}로 채팅방에 게시한다({@value #CHAT_EVENT_NAME}).
     * 참가자 MESSAGE가 아니라 EVENT 타입으로 도착하므로 FE가 이 타입을 렌더링해야 보이고,
     * 판매자 화면이 자기 토큰으로 직접 올리던 게시는 걷어내야 두 번 보이지 않는다.
     * 게시 실패는 답변 저장을 막지 않는다.
     */
    @Transactional
    public LiveQuestionSummaryJpaEntity send(UUID sellerId, UUID liveId, UUID questionId, String finalAnswer) {
        if (finalAnswer == null || finalAnswer.isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "답변 내용이 비어 있습니다.");
        }
        LiveSession session = loadOwned(sellerId, liveId);
        LiveQuestionSummaryJpaEntity summary = loadSummaryOf(session, questionId);
        AiClient.SellerAnswerResult result = aiClient.registerSellerAnswer(
                liveId.toString(), summary.getAiQuestionId(), finalAnswer);
        if (!result.liveKnowledgeRegistered()) {
            // ponytail: 재시도 없이 로그만 남긴다. 판매자 답변 자체는 아래 recordAnswer로 저장되니
            // 재현 이후 조회는 정상이다 — 다음 유사 질문에 AI가 이 답변을 재사용하지 못할
            // 가능성만 남는다. 운영에서 반복되면 재시도 큐로 옮긴다.
            log.warn("AI Live Knowledge 등록 실패, liveId={}, questionId={}", liveId, questionId);
        }
        summary.recordAnswer(finalAnswer, Instant.now());
        if (session.getIvsChatRoomArn() != null) {
            eventPublisher.publishEvent(
                    new SellerAnswerSent(session.getIvsChatRoomArn(), liveId, questionId, finalAnswer));
        }
        return summary;
    }

    /**
     * 커밋 뒤 별도 스레드에서 게시한다({@code CueSheetService.onCueSheetGenerationRequested}와 같은
     * 방식). 트랜잭션 안에서 보내면 롤백됐을 때 채팅엔 답변이 떠 있고, 커밋 콜백에서 바로 보내면
     * 그동안 DB 커넥션과 판매자 요청이 IVS 응답을 기다린다. 트랜잭션 밖에서 발행돼도 실행된다
     * ({@code fallbackExecution}).
     *
     * <p>package-private — 테스트에서 Spring 이벤트 시스템 없이 직접 호출하기 위해서다.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onSellerAnswerSent(SellerAnswerSent event) {
        try {
            ivsClient.sendChatEvent(event.roomArn(), CHAT_EVENT_NAME,
                    chatEventAttributes(event.questionId(), event.answer()));
        } catch (RuntimeException e) {
            // ponytail: 재시도 없이 로그만 남긴다. 운영에서 반복되면 재시도 큐로 옮긴다.
            log.warn("채팅 게시 실패, liveId={}, questionId={}", event.liveId(), event.questionId(), e);
        }
    }

    record SellerAnswerSent(String roomArn, UUID liveId, UUID questionId, String answer) {
    }

    /** 한도를 넘는 긴 답변은 {@code questionId}만 보낸다 — FE가 {@code answered-questions}로 조회한다. */
    static Map<String, String> chatEventAttributes(UUID questionId, String answer) {
        Map<String, String> full = Map.of("questionId", questionId.toString(), "answer", answer);
        return IvsClient.chatEventAttributesBytes(full) <= CHAT_EVENT_ATTRIBUTES_MAX_BYTES
                ? full : Map.of("questionId", questionId.toString());
    }

    private LiveSession loadOwned(UUID sellerId, UUID liveId) {
        return sessionRepository.findOwned(liveId, sellerId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    /** 요청한 LIVE에 속한 질문만 돌려준다 — 남의 questionId면 404다(S4·S10). */
    private LiveQuestionSummaryJpaEntity loadSummaryOf(LiveSession session, UUID questionId) {
        return summaryRepository.findByPublicIdAndSessionId(questionId, session.getId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
