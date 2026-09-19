package com.fundit.live.application.question;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.application.ai.AiClient;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import com.fundit.live.infrastructure.persistence.question.LiveQuestionSummaryJpaEntity;
import com.fundit.live.infrastructure.persistence.question.LiveQuestionSummaryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * AI 추천답변 생성/전송(요구사항정의서 6.4.4.5·6.4.4.6).
 *
 * <p><b>생성과 전송이 분리돼 있다.</b> {@code GENERATE}는 초안만 만들고 채팅에 게시하지 않는다 —
 * 협의 결정이 "AI 추천 답변은 판매자 승인 후 전송"이다. 한 호출로 합치면 승인 단계가 사라진다.
 */
@Service
@RequiredArgsConstructor
public class AiAnswerService {

    private final LiveQuestionSummaryJpaRepository summaryRepository;
    private final LiveSessionRepository sessionRepository;
    private final AiClient aiClient;

    /**
     * 초안만 만든다. 이 호출은 채팅에 아무것도 남기지 않는다.
     *
     * <p>{@code grounded=false}는 상품정보에서 근거를 찾지 못했다는 뜻이며 에러가 아니다 —
     * 503으로 올리면 화면이 "관련 상품정보가 없습니다"를 그릴 수 없다(요구사항정의서 6.4.4.5).
     */
    @Transactional(readOnly = true)
    public AiClient.AnswerDraft generate(UUID sellerId, UUID liveId, UUID questionId,
                                         List<String> productContext) {
        LiveQuestionSummaryJpaEntity summary = loadSummaryOf(loadOwned(sellerId, liveId), questionId);
        return aiClient.generateAnswer(liveId.toString(), summary.getSummaryText(), productContext);
    }

    /**
     * 판매자가 확인·수정한 최종 문구를 답변으로 <b>기록</b>한다.
     *
     * <p>저장하는 이유: 채팅 스트림은 지나가면 끝이라, 남기지 않으면 소비자 Q&A 버튼
     * (요구사항정의서 11.3.4)이 "답변들을 모아본다"를 할 수 없다.
     *
     * <p><b>[천장] 채팅 게시는 아직 판매자 화면이 자기 채팅 토큰으로 직접 한다.</b>
     * BE가 대신 게시하려면 IVS Chat {@code SendMessage} 클라이언트가 필요한데,
     * 지금은 IVS 연동 전체가 스텁이라({@code live.ivs.mode=stub}) 만들 대상이 없다.
     * AWS 자격증명이 내려와 {@code AwsIvsClient}가 붙는 시점에 이 메서드가
     * "게시 → 기록" 순서를 갖게 하고, 재시도 중복 게시는 {@code answered} 플래그로 막는다.
     */
    @Transactional
    public LiveQuestionSummaryJpaEntity send(UUID sellerId, UUID liveId, UUID questionId, String finalAnswer) {
        if (finalAnswer == null || finalAnswer.isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "답변 내용이 비어 있습니다.");
        }
        LiveQuestionSummaryJpaEntity summary = loadSummaryOf(loadOwned(sellerId, liveId), questionId);
        summary.recordAnswer(finalAnswer, Instant.now());
        return summary;
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
