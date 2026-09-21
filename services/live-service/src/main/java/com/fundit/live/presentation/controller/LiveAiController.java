package com.fundit.live.presentation.controller;

import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.live.application.ai.AiClient;
import com.fundit.live.application.cuesheet.CueSheetService;
import com.fundit.live.application.question.AiAnswerService;
import com.fundit.live.application.question.QuestionInsightService;
import com.fundit.live.presentation.dto.AiAnswerRequest;
import com.fundit.live.presentation.dto.AiAnswerResponse;
import com.fundit.live.presentation.dto.AnsweredQuestionResponse;
import com.fundit.live.presentation.dto.CueSheetGenerateRequest;
import com.fundit.live.presentation.dto.CueSheetResponse;
import com.fundit.live.presentation.dto.CueSheetUpdateRequest;
import com.fundit.live.presentation.dto.InsightsResponse;
import com.fundit.live.presentation.dto.OriginalMessageResponse;
import com.fundit.live.presentation.dto.UnansweredResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * AI 큐시트·대표질문 계열. 인가는 전부 소유권 검증이다.
 *
 * <p>비동기 생성(큐시트)은 202를 돌려주고 FE가 {@code GET}의 status를 폴링한다.
 * Q&A/FAQ는 반대로 전부 동기 조회다 — <b>FE는 여전히 AI를 직접 호출하지 않는다</b>
 * (AI ↔ BE/FE 협의 확정, 호출 방향만 BE→AI로 바뀌었다).
 */
@RestController
@RequestMapping("/api/v1/lives/{liveId}")
@RequiredArgsConstructor
public class LiveAiController {

    private final CueSheetService cueSheetService;
    private final QuestionInsightService questionInsightService;
    private final AiAnswerService aiAnswerService;

    /** AI 큐시트 생성 요청(요구사항정의서 6.2.4.2). 방송 길이 10분 초과면 400. */
    @PostMapping("/cue-sheet")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CueSheetResponse requestCueSheet(@LoginUser CurrentUser user, @PathVariable UUID liveId,
                                            @Valid @RequestBody CueSheetGenerateRequest request) {
        cueSheetService.requestGeneration(user.id(), liveId, request.mode().name(), request.targetDurationSec(),
                request.demoAvailableOrFalse(), request.emphasisOrEmpty(), request.tone(), request.mandatoryOrEmpty());
        return new CueSheetResponse("GENERATING", request.mode().name(), request.targetDurationSec(), null, null);
    }

    /** 큐시트 결과 조회. jobId를 따로 두지 않는다 — 세션당 1개라 이 status로 폴링하면 충분하다. */
    @GetMapping("/cue-sheet")
    public CueSheetResponse getCueSheet(@LoginUser CurrentUser user, @PathVariable UUID liveId) {
        return CueSheetResponse.from(cueSheetService.find(user.id(), liveId));
    }

    /** 판매자 직접 수정. 구간 추가·순서 변경도 이 경로다. */
    @PatchMapping("/cue-sheet")
    public CueSheetResponse updateCueSheet(@LoginUser CurrentUser user, @PathVariable UUID liveId,
                                           @Valid @RequestBody CueSheetUpdateRequest request) {
        return CueSheetResponse.from(
                cueSheetService.replaceSegments(user.id(), liveId, request.segmentsJson()));
    }

    /** AI 집계 Q&A(요구사항정의서 6.4.4.2). AI가 이미 집계·정렬한 결과를 그대로 내려준다. */
    @GetMapping("/chat/insights")
    public InsightsResponse insights(@LoginUser CurrentUser user, @PathVariable UUID liveId,
                                     @RequestParam(defaultValue = "10") int topN) {
        return InsightsResponse.from(questionInsightService.faq(user.id(), liveId, topN));
    }

    /** 대표질문 원본 채팅(요구사항정의서 6.4.4.3). */
    @GetMapping("/chat/questions/{questionId}")
    public List<OriginalMessageResponse> originalMessages(@LoginUser CurrentUser user,
                                                          @PathVariable UUID liveId,
                                                          @PathVariable UUID questionId) {
        return questionInsightService.originalMessages(user.id(), liveId, questionId).stream()
                .map(OriginalMessageResponse::from)
                .toList();
    }

    /**
     * 미답변 질문 판매자 답변(요구사항정의서 6.4.4.5·6.4.4.6).
     * {@code GENERATE}는 초안 미리보기만 한다 — {@code SEND}를 호출해야 등록된다.
     */
    @PostMapping("/chat/questions/{questionId}/ai-answer")
    public AiAnswerResponse aiAnswer(@LoginUser CurrentUser user, @PathVariable UUID liveId,
                                     @PathVariable UUID questionId,
                                     @Valid @RequestBody AiAnswerRequest request) {
        if (request.isSend()) {
            aiAnswerService.send(user.id(), liveId, questionId, request.finalAnswer());
            return new AiAnswerResponse(request.finalAnswer(), List.of(), true);
        }
        AiClient.UnansweredDetail detail = aiAnswerService.draft(user.id(), liveId, questionId);
        List<String> referenceChunks = detail.reference() == null ? List.of()
                : detail.reference().chunks().stream().map(AiClient.ReferenceChunk::text).toList();
        return new AiAnswerResponse(detail.draft(), referenceChunks, false);
    }

    /** 미답변 질문 창(요구사항정의서 6.4.4.5). 근거를 못 찾은 질문만 모인다. */
    @GetMapping("/chat/unanswered")
    public UnansweredResponse unanswered(@LoginUser CurrentUser user, @PathVariable UUID liveId,
                                         @RequestParam(defaultValue = "10") int topN) {
        return UnansweredResponse.from(questionInsightService.unanswered(user.id(), liveId, topN));
    }

    /** 답변된 질문 모아보기(요구사항정의서 11.3.4) — 채팅창 Q&A 버튼. 인증 불필요. */
    @GetMapping("/chat/answered-questions")
    public List<AnsweredQuestionResponse> answeredQuestions(@PathVariable UUID liveId) {
        return questionInsightService.answeredQuestions(liveId).stream()
                .map(AnsweredQuestionResponse::from)
                .toList();
    }
}
