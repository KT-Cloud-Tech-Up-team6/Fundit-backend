package com.fundit.live.presentation.controller;

import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.live.application.chat.ChatTokenService;
import com.fundit.live.application.session.LiveCreateService;
import com.fundit.live.application.session.LivePlaybackService;
import com.fundit.live.application.session.LiveQueryService;
import com.fundit.live.application.session.LiveSettingsService;
import com.fundit.live.application.session.LiveStreamService;
import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.presentation.dto.ChatTokenResponse;
import com.fundit.live.presentation.dto.LiveCreateRequest;
import com.fundit.live.presentation.dto.PlaybackResponse;
import com.fundit.live.presentation.dto.LiveCreateResponse;
import com.fundit.live.presentation.dto.LiveSettingsRequest;
import com.fundit.live.presentation.dto.LiveStatusResponse;
import com.fundit.live.presentation.dto.LiveSummaryResponse;
import com.fundit.live.presentation.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
 * 모든 판매자 엔드포인트의 인가는 <b>소유권 검증</b>이다 — 이 서비스에 판매자 역할 검사는 없다.
 * 요구사항정의서 2.1.4가 "하나의 권한으로 판매자/구매자 페이지에 접근"으로 정의했고
 * {@code accounts.role}도 단일 값이다.
 *
 * <p>{@code X-User-Id}를 직접 파싱하지 않고 {@code @LoginUser CurrentUser}로 받는다(루트 CLAUDE.md).
 */
@RestController
@RequestMapping("/api/v1/lives")
@RequiredArgsConstructor
public class LiveController {

    private final LiveCreateService liveCreateService;
    private final LiveSettingsService liveSettingsService;
    private final LiveStreamService liveStreamService;
    private final LiveQueryService liveQueryService;
    private final LivePlaybackService livePlaybackService;
    private final ChatTokenService chatTokenService;

    /** LIVE 생성(요구사항정의서 6.1.4). 본인 소유 프로젝트만. 생성 직후 DRAFT다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LiveCreateResponse create(@LoginUser CurrentUser user,
                                     @Valid @RequestBody LiveCreateRequest request) {
        return LiveCreateResponse.from(liveCreateService.create(user.id(), request.projectId()));
    }

    /**
     * 내 LIVE 목록(요구사항정의서 6.1.3). 소비자 목록으로 대체할 수 없다 —
     * 그쪽은 비인증이고 DRAFT를 빼므로 임시저장한 LIVE로 돌아갈 경로가 사라진다.
     */
    @GetMapping("/mine")
    public PageResponse<LiveSummaryResponse> findMine(@LoginUser CurrentUser user,
                                                      @RequestParam(required = false) LiveStatus status,
                                                      @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(liveQueryService.findMine(user.id(), status, pageable)
                .map(LiveSummaryResponse::from));
    }

    /** LIVE 기본 설정 등록/수정(요구사항정의서 6.2.4.1). 부분 업데이트다. */
    @PatchMapping("/{liveId}/settings")
    public LiveStatusResponse updateSettings(@LoginUser CurrentUser user,
                                             @PathVariable UUID liveId,
                                             @Valid @RequestBody LiveSettingsRequest request) {
        return LiveStatusResponse.from(liveSettingsService.update(
                user.id(), liveId, request.majorOrNull(), request.minorOrNull(),
                request.introText(), request.thumbnailUrl(), request.scheduledStartAt()));
    }

    /** LIVE 시작(요구사항정의서 6.3.4). */
    @PostMapping("/{liveId}/start")
    public LiveStatusResponse start(@LoginUser CurrentUser user, @PathVariable UUID liveId) {
        return LiveStatusResponse.from(liveStreamService.start(user.id(), liveId));
    }

    /** LIVE 종료(요구사항정의서 6.3.4). */
    @PostMapping("/{liveId}/end")
    public LiveStatusResponse end(@LoginUser CurrentUser user, @PathVariable UUID liveId) {
        return LiveStatusResponse.from(liveStreamService.end(user.id(), liveId));
    }

    /** 소비자 LIVE 목록(요구사항정의서 11.1.4). 인증 불필요. DRAFT는 절대 나오지 않는다. */
    @GetMapping
    public PageResponse<LiveSummaryResponse> findPublic(@RequestParam(required = false) LiveStatus status,
                                                        @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(liveQueryService.findPublic(status, pageable)
                .map(LiveSummaryResponse::from));
    }

    /** 진행중 LIVE 배너(요구사항정의서 10.1.4). 인증 불필요. */
    @GetMapping("/banner")
    public List<LiveSummaryResponse> banner() {
        return liveQueryService.findLiveBanner().stream().map(LiveSummaryResponse::from).toList();
    }

    /**
     * IVS Chat 접속 토큰 발급(요구사항정의서 6.4.4.1). 판매자·소비자 공통 경로다 —
     * 호출자가 방송 소유자인지 보고 capabilities를 정한다.
     */
    @PostMapping("/{liveId}/chat/token")
    public ChatTokenResponse chatToken(@LoginUser CurrentUser user, @PathVariable UUID liveId) {
        return ChatTokenResponse.from(chatTokenService.issue(user.id(), liveId));
    }

    /** LIVE 시청 정보(요구사항정의서 11.2.4). 종료된 방송은 다시보기로 자동 전환된다. */
    @GetMapping("/{liveId}/playback")
    public PlaybackResponse playback(@PathVariable UUID liveId) {
        return PlaybackResponse.from(livePlaybackService.playback(liveId));
    }

    /** 다시보기 재생 정보(요구사항정의서 11.4.4). */
    @GetMapping("/{liveId}/vod")
    public PlaybackResponse vod(@PathVariable UUID liveId) {
        return PlaybackResponse.from(livePlaybackService.vod(liveId));
    }
}
