package com.fundit.live.presentation.controller;

import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.live.application.highlight.HighlightService;
import com.fundit.live.presentation.dto.HighlightEditRequest;
import com.fundit.live.presentation.dto.HighlightResponse;
import com.fundit.live.presentation.dto.HighlightStatsResponse;
import com.fundit.live.presentation.dto.HighlightVisibilityRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** LIVE 하이라이트(요구사항정의서 6.6.4). 판매자 경로는 전부 소유권 검증이다. */
@RestController
@RequestMapping("/api/v1/lives/{liveId}/highlights")
@RequiredArgsConstructor
public class LiveHighlightController {

    private final HighlightService highlightService;

    /** 자동 생성 요청. 다시보기가 없으면 409다. */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestGeneration(@LoginUser CurrentUser user, @PathVariable UUID liveId) {
        highlightService.requestGeneration(user.id(), liveId);
    }

    /** 판매자 검토용 목록 — 비공개·실패분까지 전부 보인다. */
    @GetMapping
    public HighlightResponse findAll(@LoginUser CurrentUser user, @PathVariable UUID liveId) {
        return HighlightResponse.from(highlightService.findAll(user.id(), liveId));
    }

    /** 소비자 공개 목록. 인증 불필요이고 이 호출이 조회 수로 잡힌다. */
    @GetMapping("/public")
    public HighlightResponse findPublic(@PathVariable UUID liveId) {
        return HighlightResponse.from(highlightService.findPublic(liveId));
    }

    @PatchMapping("/{highlightId}")
    public HighlightResponse.Item edit(@LoginUser CurrentUser user, @PathVariable UUID liveId,
                                       @PathVariable UUID highlightId,
                                       @Valid @RequestBody HighlightEditRequest request) {
        return HighlightResponse.Item.from(highlightService.edit(user.id(), liveId, highlightId,
                request.startSec(), request.endSec(), request.sceneLabel(),
                request.title(), request.caption()));
    }

    @PostMapping("/{highlightId}/regenerate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void regenerate(@LoginUser CurrentUser user, @PathVariable UUID liveId,
                           @PathVariable UUID highlightId) {
        highlightService.regenerate(user.id(), liveId, highlightId);
    }

    @DeleteMapping("/{highlightId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@LoginUser CurrentUser user, @PathVariable UUID liveId,
                       @PathVariable UUID highlightId) {
        highlightService.delete(user.id(), liveId, highlightId);
    }

    /** 공개 설정. 생성 실패분은 공개할 수 없다 — 재생 불가한 클립이 소비자 화면에 올라간다. */
    @PatchMapping("/{highlightId}/visibility")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeVisibility(@LoginUser CurrentUser user, @PathVariable UUID liveId,
                                 @PathVariable UUID highlightId,
                                 @Valid @RequestBody HighlightVisibilityRequest request) {
        highlightService.changeVisibility(user.id(), liveId, highlightId, request.isPublic());
    }

    /** 클립 클릭 기록. 전환 동선 추적용이라 인증을 요구하지 않는다. */
    @PostMapping("/{highlightId}/click")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordClick(@PathVariable UUID liveId, @PathVariable UUID highlightId) {
        highlightService.recordClick(liveId, highlightId);
    }

    /** 성과 통계. 펀딩 전환 기여는 집계 주체 미정이라 아직 포함하지 않는다. */
    @GetMapping("/stats")
    public List<HighlightStatsResponse> stats(@LoginUser CurrentUser user, @PathVariable UUID liveId) {
        return highlightService.findAll(user.id(), liveId).stream()
                .map(HighlightStatsResponse::from).toList();
    }
}
