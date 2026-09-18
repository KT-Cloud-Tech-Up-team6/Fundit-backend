package com.fundit.live.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.application.chat.ChatIngestService;
import com.fundit.live.application.cuesheet.CueSheetService;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import com.fundit.live.presentation.dto.ChatIngestRequest;
import com.fundit.live.presentation.dto.InternalLiveStatusResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
    private final LiveSessionJpaRepository sessionRepository;
    private final LiveChannelJpaRepository channelRepository;

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
        LiveSessionJpaEntity session = sessionRepository.findByPublicId(liveId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        UUID sellerId = channelRepository.findById(session.getChannelId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND))
                .getSellerId();
        return new InternalLiveStatusResponse(session.getPublicId(), session.getId(),
                session.getStatus().name(), sellerId);
    }

    /**
     * AI 큐시트 생성 결과 수신. AI가 완료되면 이 경로로 밀어준다 —
     * 우리가 폴링하면 스케줄러와 job 식별자 컬럼이 따라붙는데 얻는 게 없다.
     *
     * <p>외부 응답을 그대로 신뢰하지 않고 필요한 값의 존재를 확인한 뒤 저장한다(S7).
     */
    @PostMapping("/internal/v1/lives/{liveId}/cue-sheet")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void applyCueSheet(@PathVariable UUID liveId, @RequestBody CueSheetCallback callback) {
        cueSheetService.applyResult(liveId, callback.status(), callback.segments(), callback.failureReason());
    }

    /** AI가 돌려주는 큐시트 결과. status는 COMPLETED 또는 FAILED다. */
    public record CueSheetCallback(String status, String segments, String failureReason) {
    }
}
