package com.fundit.live.infrastructure.ai;

import com.fundit.live.application.ai.AiClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 서버 주소도 계약도 확정 전이라 쓰는 스텁. {@code live.ai.mode=stub}(기본값)일 때 뜬다.
 *
 * <p>비동기 요청은 아무것도 하지 않는다 — 결과는 AI가 내부 엔드포인트로 밀어주는 구조라
 * 스텁이 흉내 낼 대상이 없다. 그 경로는 내부 컨트롤러 테스트가 직접 호출해 검증한다.
 */
@Component
@ConditionalOnProperty(name = "live.ai.mode", havingValue = "stub", matchIfMissing = true)
public class StubAiClient implements AiClient {

    @Override
    public void requestCueSheet(String liveId, CueSheetRequest request) {
        // 요청만 거는 경로다. 스텁은 성공으로 두고, 결과 수신은 내부 엔드포인트가 담당한다.
    }

    @Override
    public void requestHighlights(String liveId, String vodUrl) {
        // 위와 같다.
    }

    @Override
    public AnswerDraft generateAnswer(String liveId, String questionText, List<String> productContext) {
        // 상품정보가 비어 있으면 근거가 없다 — grounded=false가 그 상태다.
        if (productContext.isEmpty()) {
            return new AnswerDraft(null, false);
        }
        return new AnswerDraft("[stub] " + questionText + "에 대한 답변 초안", true);
    }

    @Override
    public boolean isReady(String liveId) {
        return true;
    }
}
