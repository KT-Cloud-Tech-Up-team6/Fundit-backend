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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
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

    static final String CHAT_EVENT_NAME = "seller-answer";
    /** IVS Chat SendEvent attributes 합계 상한(AWS API 문서: "4 KB total"). */
    static final int CHAT_EVENT_ATTRIBUTES_MAX_BYTES = 4 * 1024;

    /** 초안 미리보기. 이 호출은 아무것도 기록하지 않는다. */
    @Transactional(readOnly = true)
    public AiClient.UnansweredDetail draft(UUID sellerId, UUID liveId, UUID questionId) {
        LiveQuestionSummaryJpaEntity summary = loadSummaryOf(loadOwned(sellerId, liveId), questionId);
        return aiClient.unansweredDetail(liveId.toString(), summary.getAiQuestionId());
    }

    /**
     * 판매자가 확인·수정한 최종 문구를 AI에 등록하고 로컬에도 <b>기록</b>한다.
     *
     * <p>AI 등록이 성공해야 로컬에 남긴다 — AI 쪽이 실패했는데 로컬만 "답변 완료"로 표시되면
     * 다음 유사 질문이 다시 미답변으로 잡히는데 화면은 이미 답변된 것으로 보여준다.
     *
     * <p>커밋 뒤 IVS Chat {@code SendEvent}로 채팅방에 게시한다({@value #CHAT_EVENT_NAME}).
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
        postToChatAfterCommit(session, questionId, finalAnswer);
        return summary;
    }

    /**
     * 커밋 뒤에 게시한다 — 트랜잭션 안에서 보내면 커밋이 실패했을 때 채팅엔 답변이 떴는데
     * 저장은 롤백된 상태가 된다. 트랜잭션 밖(단위 테스트 등)에선 바로 보낸다.
     */
    private void postToChatAfterCommit(LiveSession session, UUID questionId, String answer) {
        if (session.getIvsChatRoomArn() == null) {
            return;
        }
        Runnable post = () -> postToChat(session.getIvsChatRoomArn(), session.getPublicId(), questionId, answer);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            post.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                post.run();
            }
        });
    }

    private void postToChat(String roomArn, UUID liveId, UUID questionId, String answer) {
        try {
            ivsClient.sendChatEvent(roomArn, CHAT_EVENT_NAME, chatEventAttributes(questionId, answer));
        } catch (RuntimeException e) {
            // ponytail: 재시도 없이 로그만 남긴다. 운영에서 반복되면 재시도 큐로 옮긴다.
            log.warn("채팅 게시 실패, liveId={}, questionId={}", liveId, questionId, e);
        }
    }

    /** 한도를 넘는 긴 답변은 {@code questionId}만 보낸다 — FE가 {@code answered-questions}로 조회한다. */
    static Map<String, String> chatEventAttributes(UUID questionId, String answer) {
        Map<String, String> full = Map.of("questionId", questionId.toString(), "answer", answer);
        int bytes = full.entrySet().stream()
                .mapToInt(e -> utf8Length(e.getKey()) + utf8Length(e.getValue()))
                .sum();
        return bytes <= CHAT_EVENT_ATTRIBUTES_MAX_BYTES ? full : Map.of("questionId", questionId.toString());
    }

    private static int utf8Length(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
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
