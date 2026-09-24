package com.fundit.search.application.live;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * LIVE 카드 조회 포트. search-service는 LIVE 원본을 소유하지 않으므로 live-service 공개 목록
 * API를 그대로 부른다(SearchERD.md 5-③).
 *
 * <p>색인({@code live_documents}) 대신 동기 호출을 고른 이유: {@code live.started.v1}/
 * {@code live.ended.v1} 페이로드에 카드 필드({@code introText}·{@code thumbnailUrl}·
 * {@code scheduledStartAt}·{@code likeCount})가 하나도 없고, SCHEDULED 전이 이벤트 자체가 없어
 * 예정 LIVE가 영원히 색인되지 않는다. LIVE 건수는 프로젝트와 자릿수가 달라 프록시로 충분하다 —
 * 지연이 실제로 문제가 되면 그때 컨슈머를 붙인다.
 */
public interface LiveCardClient {

    /** 검색 LIVE 탭(SEARCH-006). DRAFT는 live-service 쿼리에서 이미 제외된다. */
    Page<LiveCard> findPublic(Pageable pageable);

    /** 홈 LIVE 섹션(SEARCH-002) — 현재 방송 중인 것만. */
    List<LiveCard> findBanner();

    /**
     * live-service {@code LiveSummaryResponse}와 필드 1:1. 이름을 바꾸면 FE가 LIVE 메인과
     * 검색 결과에 같은 카드 컴포넌트를 못 쓰므로 그대로 미러링한다.
     *
     * <p>{@code title}이 없는 건 누락이 아니다 — LIVE에는 제목 입력 자체가 없고(요구사항정의서
     * 6.2.4.1) 카드 문구는 {@code introText}다. {@code viewerCount}는 live-service가
     * {@code sort=viewerCount}(실시간 순위)일 때만 채우므로 여기서는 항상 null이다.
     */
    record LiveCard(UUID liveId, String introText, String status, UUID projectId, String thumbnailUrl,
                    Instant scheduledStartAt, int likeCount, Instant createdAt, Integer viewerCount) {
    }
}
