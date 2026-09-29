package com.fundit.live.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code action}이 GENERATE면 AI 초안 미리보기만 반환한다 — 아무것도 기록하지 않는다.
 * SEND여야 AI에 등록되고 소비자 Q&A 버튼에 노출되며, 채팅 게시도 BE가 한다
 * ({@code AiAnswerService#send} 참고).
 *
 * <p>MARK_DONE은 "방송 중 말로 답했다" 표시다 — 고정 문구만 로컬에 남기고 AI 등록·채팅 게시는 하지 않는다
 * ({@code AiAnswerService#markDone} 참고). {@code finalAnswer}는 쓰지 않는다.
 *
 * <p>이 흐름은 AI가 근거를 못 찾은(UNANSWERABLE) 질문 전용이다 — 근거를 찾은 질문은
 * 채팅 배치 응답으로 이미 즉시 답변되어 있다.
 *
 * <p>{@code finalAnswer} 1000자 제한은 코파일럿 {@code answer_text}(1~1000자 필수)에 맞춘 것이다 —
 * 넘기면 코파일럿에서 실패해 503으로 보인다.
 */
public record AiAnswerRequest(@NotNull Action action, @Size(max = 1000) String finalAnswer) {

    /** 문자열로 두면 오타가 조용히 GENERATE로 흘러 판매자가 보낸 줄 아는 답변이 등록되지 않는다. */
    public enum Action {GENERATE, SEND, MARK_DONE}

    public boolean isSend() {
        return action == Action.SEND;
    }

    public boolean isMarkDone() {
        return action == Action.MARK_DONE;
    }
}
