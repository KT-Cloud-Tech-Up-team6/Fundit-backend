package com.fundit.live.presentation.controller;

import com.fundit.live.application.chat.ChatIngestService;
import com.fundit.live.application.cuesheet.CueSheetService;
import com.fundit.live.application.highlight.HighlightService;
import com.fundit.live.domain.ai.GenerationStatus;
import com.fundit.live.domain.highlight.HighlightKind;
import com.fundit.live.domain.highlight.SceneLabel;
import com.fundit.live.application.session.LiveStatusQueryService;
import com.fundit.live.presentation.dto.ChatIngestRequest;
import com.fundit.live.presentation.dto.InternalLiveStatusResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 내부 전용. 보호는 {@code infrastructure.security.InternalEndpointConfig}에 등록한
 * {@code InternalEndpoint} 빈이 담당한다(게이트웨이 라우팅에서도 제외해야 한다).
 */
@RestController
@RequiredArgsConstructor
public class InternalLiveController {

    private final ChatIngestService chatIngestService;
    private final CueSheetService cueSheetService;
    private final HighlightService highlightService;
    private final LiveStatusQueryService liveStatusQueryService;

    /**
     * 채팅 적재(요구사항정의서 11.3.4). Firehose는 재전송이 가능해서 같은 메시지가 두 번 온다 —
     * 중복은 정상 흐름이므로 에러가 아니라 204로 조용히 흘린다.
     */
    @PostMapping("/internal/v1/lives/chat/messages")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ingestChat(@Valid @RequestBody ChatIngestRequest request) {
        chatIngestService.ingest(request.roomArn(), request.ivsMessageId(), request.senderId(),
                request.content(), request.sentAt());
    }

    /** 방송 진행 상태 조회 — order-service의 라이브 쿠폰 검증용. */
    @GetMapping("/internal/v1/lives/{liveId}/status")
    public InternalLiveStatusResponse status(@PathVariable UUID liveId) {
        LiveStatusQueryService.LiveStatus s = liveStatusQueryService.find(liveId);
        return new InternalLiveStatusResponse(s.liveId(), s.sessionId(), s.status(), s.sellerId());
    }

    /**
     * AI 큐시트 생성 결과 수신. AI가 완료되면 이 경로로 밀어준다 —
     * 우리가 폴링하면 스케줄러와 job 식별자 컬럼이 따라붙는데 얻는 게 없다.
     *
     * <p>외부 응답을 그대로 신뢰하지 않고 필요한 값의 존재를 확인한 뒤 저장한다(S7).
     */
    @PostMapping("/internal/v1/lives/{liveId}/cue-sheet")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void applyCueSheet(@PathVariable UUID liveId, @Valid @RequestBody CueSheetCallback callback) {
        cueSheetService.applyResult(liveId, callback.status(), callback.segmentsJson(),
                callback.failureReason());
    }

    /**
     * AI가 돌려주는 큐시트 결과. status는 COMPLETED 또는 FAILED다.
     *
     * <p>{@code segments}가 {@code String}이던 때는 AI가 JSON이 아닌 값을 보내면
     * JSONB 컬럼이 거부해 <b>400이 아니라 500</b>이 났다. {@code JsonNode}로 받으면
     * Jackson이 파싱 단계에서 걸러낸다({@code CueSheetUpdateRequest}와 같은 방식).
     * FAILED일 때는 구간이 없으므로 null을 허용한다.
     */
    public record CueSheetCallback(@NotBlank String status, JsonNode segments, String failureReason) {

        @AssertTrue(message = "구간은 비어 있지 않은 배열이어야 합니다.")
        public boolean isSegmentsArrayWhenPresent() {
            return segments == null || (segments.isArray() && !segments.isEmpty());
        }

        public String segmentsJson() {
            return segments == null ? null : segments.toString();
        }
    }

    /**
     * AI 하이라이트 생성 결과 수신. 클립 개수 상한(방송 1회당 3개)은 여기서 서버가 검증한다 —
     * AI가 더 보내도 초과분은 받지 않는다(요구사항정의서 6.6.3).
     */
    @PostMapping("/internal/v1/lives/{liveId}/highlights")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void applyHighlights(@PathVariable UUID liveId,
                                @RequestBody @Valid List<@Valid HighlightCallback> callbacks) {
        highlightService.applyGenerated(liveId, callbacks.stream()
                .map(c -> new HighlightService.GeneratedHighlight(c.highlightId(), c.kind(),
                        c.sceneLabel(), c.title(), c.startSec(), c.endSec(), c.clipUrl(),
                        c.caption(), c.status()))
                .toList());
    }

    /**
     * AI가 돌려주는 하이라이트 1건. status는 COMPLETED 또는 FAILED다.
     *
     * <p>필수값을 검증하는 이유: 비면 NOT NULL 제약 위반으로 <b>400이 아니라 500</b>이 난다.
     * AI가 잘못 보낸 건데 우리 서버 오류로 보인다.
     */
    public record HighlightCallback(UUID highlightId,
                                    // enum인 이유: 문자열이면 오타가 DB CHECK까지 가서 400이 아니라
                                    // 500이 난다. Jackson이 파싱 단계에서 400으로 돌려보낸다.
                                    @NotNull HighlightKind kind, @NotNull SceneLabel sceneLabel,
                                    String title,
                                    // 박싱인 이유: primitive면 값 누락이 조용히 0이 된다 — 0초짜리
                                    // 마커가 생겨도 아무도 모른다.
                                    @NotNull @PositiveOrZero Integer startSec,
                                    @PositiveOrZero Integer endSec,
                                    String clipUrl, String caption,
                                    @NotNull GenerationStatus status) {

        /** MARKER는 시점이라 endSec이 null이다. 있으면 뒤집힌 구간을 막는다. */
            @AssertTrue(message = "종료 위치가 시작보다 뒤여야 합니다.")
        public boolean isRangeOrdered() {
            return endSec == null || startSec == null || endSec > startSec;
        }
    }
}
