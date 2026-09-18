package com.fundit.live.application.ai;

import java.util.List;

/**
 * AI 추론 서버(`/api/v1/ai`) 연동 포트. 흐름은 FE → BE → AI → BE → FE로 통일됐고
 * <b>FE는 AI를 직접 호출하지 않는다</b>(AI ↔ BE/FE 협의 확정).
 *
 * <p>AI가 우리 DB를 읽지 않으므로 컨텍스트는 live가 조립해 넘긴다 —
 * AI가 우리 스키마에 의존하면 컬럼 하나를 바꿀 때마다 배포 일정을 맞춰야 한다.
 *
 * <p>비동기 생성(큐시트·하이라이트)은 요청만 걸고, <b>결과는 AI가 내부 엔드포인트로 밀어준다</b>
 * ({@code POST /internal/v1/lives/{liveId}/cue-sheet} 등). 우리가 폴링하면 스케줄러와
 * job 식별자 컬럼이 따라붙는데 얻는 게 없다.
 */
public interface AiClient {

    /** 큐시트 생성 요청(요구사항정의서 6.2.4.2). 반환 없이 요청만 건다. */
    void requestCueSheet(String liveId, CueSheetRequest request);

    /** 하이라이트 자동 생성 요청(요구사항정의서 6.6.4). 방송 종료 후 호출된다. */
    void requestHighlights(String liveId, String vodUrl);

    /**
     * 대표질문 추천답변 초안(요구사항정의서 6.4.4.5·6.4.4.6). 이건 동기다 —
     * 판매자가 화면에서 바로 보고 고쳐 보내는 흐름이라 기다릴 수 있는 길이다.
     */
    AnswerDraft generateAnswer(String liveId, String questionText, List<String> productContext);

    /** AI가 질문 분류에 쓸 상품정보를 준비했는지(요구사항정의서 6.4.4.4). */
    boolean isReady(String liveId);

    record CueSheetRequest(String mode, int targetDurationSec, boolean demoAvailable,
                           List<String> emphasisPoints, String tone, List<String> mandatoryPhrases) {
    }

    /**
     * {@code grounded=false}는 상품정보에서 근거를 찾지 못했다는 뜻이며 <b>에러가 아니다</b> —
     * 503으로 올리면 화면에 보여줄 게 없어지는데 요구사항정의서 6.4.4.5는 Empty State를 요구한다.
     */
    record AnswerDraft(String draftAnswer, boolean grounded) {
    }
}
