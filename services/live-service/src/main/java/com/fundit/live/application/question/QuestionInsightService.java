package com.fundit.live.application.question;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.application.ai.AiClient;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import com.fundit.live.infrastructure.persistence.chat.ChatMessageJpaEntity;
import com.fundit.live.infrastructure.persistence.chat.ChatMessageJpaRepository;
import com.fundit.live.infrastructure.persistence.question.LiveQuestionSummaryJpaEntity;
import com.fundit.live.infrastructure.persistence.question.LiveQuestionSummaryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AI 관심사/대표질문 조회(요구사항정의서 6.4.4.2·6.4.4.3).
 *
 * <p><b>이 API가 실패해도 방송·채팅은 정상이어야 한다</b>(6.4.4.2) —
 * 호출 실패를 방송 화면 전체의 오류로 처리하지 않는 건 클라이언트 몫이고,
 * 서버는 AI가 준비 안 된 상태를 에러가 아니라 상태값으로 알린다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionInsightService {

    private final LiveQuestionSummaryJpaRepository summaryRepository;
    private final ChatMessageJpaRepository chatMessageRepository;
    private final LiveSessionRepository sessionRepository;
    private final AiClient aiClient;

    public Insights insights(UUID sellerId, UUID liveId) {
        LiveSession session = loadOwned(sellerId, liveId);
        List<LiveQuestionSummaryJpaEntity> summaries =
                summaryRepository.findBySessionIdOrderByRelatedQuestionCountDesc(session.getId());

        // 관심사 집계는 대표질문의 topic을 접어서 만든다 — 같은 값을 테이블로 또 두지 않는다.
        Map<String, Integer> topics = summaries.stream()
                .filter(s -> s.getTopic() != null)
                .collect(Collectors.groupingBy(LiveQuestionSummaryJpaEntity::getTopic,
                        Collectors.summingInt(LiveQuestionSummaryJpaEntity::getRelatedQuestionCount)));

        // PREPARING과 "집계 0건"을 화면이 다른 문구로 안내해야 한다(요구사항정의서 6.4.4.4).
        // 빈 배열만 내려주면 두 상황을 구분할 수 없다.
        String aiStatus = aiClient.isReady(liveId.toString()) ? "READY" : "PREPARING";
        return new Insights(aiStatus, topics, summaries);
    }

    /** 대표질문 원본 채팅(요구사항정의서 6.4.4.3). */
    public List<ChatMessageJpaEntity> originalMessages(UUID sellerId, UUID liveId, UUID questionId) {
        LiveSession session = loadOwned(sellerId, liveId);
        // 소속을 조회에 묶는다 — 남의 questionId로 원본 채팅을 읽을 수 없어야 한다(S4)
        LiveQuestionSummaryJpaEntity summary = summaryRepository
                .findByPublicIdAndSessionId(questionId, session.getId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return chatMessageRepository.findByQuestionSummaryIdOrderBySentAtAsc(summary.getId());
    }

    /** 소비자 Q&A 버튼 — 답변된 질문만, 질문 건수 내림차순(요구사항정의서 11.3.4). 인증 불필요. */
    public List<LiveQuestionSummaryJpaEntity> answeredQuestions(UUID liveId) {
        LiveSession session = sessionRepository.findOwnedAny(liveId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return summaryRepository.findBySessionIdAndAnsweredTrueOrderByRelatedQuestionCountDesc(
                session.getId());
    }

    private LiveSession loadOwned(UUID sellerId, UUID liveId) {
        return sessionRepository.findOwned(liveId, sellerId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    public record Insights(String aiStatus, Map<String, Integer> topics,
                           List<LiveQuestionSummaryJpaEntity> representativeQuestions) {
    }
}
