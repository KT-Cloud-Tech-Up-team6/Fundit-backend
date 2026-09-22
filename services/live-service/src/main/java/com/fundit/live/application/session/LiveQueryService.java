package com.fundit.live.application.session;

import com.fundit.live.application.ivs.IvsClient;
import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaEntity;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import com.fundit.live.presentation.dto.LiveSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 목록 조회 전용. 읽기라 도메인 모델로 되돌리지 않고 JpaEntity를 그대로 읽는다 —
 * 상태 전이가 없는 경로에 Mapper를 한 번 더 태울 이유가 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LiveQueryService {

    private static final String SORT_VIEWER_COUNT = "viewerCount";

    private final LiveSessionJpaRepository sessionRepository;
    private final LiveChannelJpaRepository channelRepository;
    private final IvsClient ivsClient;

    /** 판매자 본인 LIVE 목록(요구사항정의서 6.1.3). 임시저장(DRAFT)으로 돌아가는 유일한 경로다. */
    public Page<LiveSessionJpaEntity> findMine(UUID sellerId, LiveStatus status, Pageable pageable) {
        return sessionRepository.findMine(sellerId, status, pageable);
    }

    /**
     * 소비자 목록. DRAFT 제외는 쿼리에 고정돼 있어 필터를 생략해도 새지 않는다.
     *
     * <p>{@code sort="viewerCount"}(실시간 순위)면 IVS 실시간 조회값 기준이라 DB에서 정렬할 수
     * 없다 — {@code status=LIVE}로 한정해 전량 조회 후 애플리케이션에서 정렬·페이징한다
     * ({@link #findPublicByViewerCount}). 그 외에는 기존 DB 정렬(`createdAt desc`) 그대로다.
     */
    public Page<LiveSummaryResponse> findPublic(LiveStatus status, String sort, List<UUID> sellerIds,
                                                Pageable pageable) {
        if (SORT_VIEWER_COUNT.equals(sort)) {
            return findPublicByViewerCount(pageable);
        }
        Page<LiveSessionJpaEntity> page = (sellerIds == null || sellerIds.isEmpty())
                ? sessionRepository.findPublic(status, pageable)
                : sessionRepository.findPublicBySellerIds(status, sellerIds, pageable);
        return page.map(LiveSummaryResponse::from);
    }

    /**
     * 동시 방송 수만큼 {@code GetStream} 호출이 나간다 — ponytail: 지금은 그대로 가고,
     * 트래픽이 늘어 문제가 되면 짧은 TTL 캐시를 붙인다.
     */
    private Page<LiveSummaryResponse> findPublicByViewerCount(Pageable pageable) {
        List<LiveSessionJpaEntity> liveSessions = sessionRepository.findByStatusOrderByActualStartAtDesc(LiveStatus.LIVE);
        Map<Long, String> channelArnById = channelRepository.findAllById(
                        liveSessions.stream().map(LiveSessionJpaEntity::getChannelId).distinct().toList())
                .stream().collect(Collectors.toMap(LiveChannelJpaEntity::getId, LiveChannelJpaEntity::getIvsChannelArn));

        List<LiveSummaryResponse> ranked = liveSessions.stream()
                .map(session -> Map.entry(session, ivsClient.getViewerCount(channelArnById.get(session.getChannelId()))))
                .sorted(Map.Entry.<LiveSessionJpaEntity, Integer>comparingByValue().reversed())
                .map(entry -> LiveSummaryResponse.from(entry.getKey(), entry.getValue()))
                .toList();

        int start = Math.min((int) pageable.getOffset(), ranked.size());
        int end = Math.min(start + pageable.getPageSize(), ranked.size());
        return new PageImpl<>(ranked.subList(start, end), pageable, ranked.size());
    }

    /** 홈 배너(요구사항정의서 10.1.4) — 현재 방송 중인 것만. */
    public List<LiveSessionJpaEntity> findLiveBanner() {
        return sessionRepository.findByStatusOrderByActualStartAtDesc(LiveStatus.LIVE);
    }
}
