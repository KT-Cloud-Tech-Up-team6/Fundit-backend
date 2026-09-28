package com.fundit.search.infrastructure.live;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.search.application.live.LiveCardClient;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * live-service 공개 목록 API 어댑터. 내부 API 키를 싣지 않는다 — 두 엔드포인트 모두
 * {@code X-User-Id}도 내부 전용 경로도 아니라 {@code InternalGatewaySecretFilter}를 그냥 통과한다.
 */
@Component
@RequiredArgsConstructor
public class LiveServiceLiveCardClient implements LiveCardClient {

    private final RestClient liveServiceRestClient;

    @Override
    public Page<LiveCard> findPublic(Pageable pageable) {
        try {
            LivePage page = liveServiceRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/lives")
                            .queryParam("page", pageable.getPageNumber())
                            .queryParam("size", pageable.getPageSize())
                            .build())
                    .retrieve()
                    .body(LivePage.class);
            if (page == null || page.content() == null) {
                return new PageImpl<>(List.of(), pageable, 0);
            }
            // totalElements는 live-service가 센 값을 그대로 쓴다 — content.size()로 재계산하면
            // 마지막 페이지에서 전체 건수가 페이지 크기로 줄어든다.
            return new PageImpl<>(page.content(), pageable, page.totalElements());
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    @Override
    public List<LiveCard> findBanner() {
        try {
            LiveCard[] banner = liveServiceRestClient.get()
                    .uri("/api/v1/lives/banner")
                    .retrieve()
                    .body(LiveCard[].class);
            return banner == null ? List.of() : List.of(banner);
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    /** live-service PageResponse 중 이 어댑터가 쓰는 두 필드만 받는다(나머지는 Pageable로 재구성). */
    private record LivePage(List<LiveCard> content, long totalElements) {
    }
}
