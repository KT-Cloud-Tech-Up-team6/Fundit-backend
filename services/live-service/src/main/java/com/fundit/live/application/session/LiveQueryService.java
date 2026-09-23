package com.fundit.live.application.session;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.application.ivs.IvsClient;
import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaEntity;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import com.fundit.live.infrastructure.persistence.session.query.LiveStatusCountProjection;
import com.fundit.live.presentation.dto.LiveDetailResponse;
import com.fundit.live.presentation.dto.LiveStatusCountsResponse;
import com.fundit.live.presentation.dto.LiveSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
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
    private static final List<LiveStatus> ALL_STATUSES = List.of(LiveStatus.values());

    private final LiveSessionJpaRepository sessionRepository;
    private final LiveChannelJpaRepository channelRepository;
    private final IvsClient ivsClient;

    /**
     * 판매자 본인 LIVE 목록(요구사항정의서 6.1.3). 임시저장(DRAFT)으로 돌아가는 유일한 경로다.
     *
     * <p>{@code statuses}가 비었으면 전체 상태로 채워 넘긴다 — JPQL {@code in}은 컬렉션
     * 파라미터가 null이면 바인딩이 실패해서, "필터 없음"을 null 대신 "전체 목록"으로 표현한다.
     */
    public Page<LiveSessionJpaEntity> findMine(UUID sellerId, List<LiveStatus> statuses, UUID projectId,
                                               String q, Pageable pageable) {
        List<LiveStatus> effectiveStatuses = (statuses == null || statuses.isEmpty()) ? ALL_STATUSES : statuses;
        return sessionRepository.findMine(sellerId, effectiveStatuses, projectId, q, pageable);
    }

    /** 판매자 본인 LIVE 단건 상세 — 임시저장 불러오기·설정 재진입·방송 중 화면(FE 요청). */
    public LiveDetailResponse findOwnedDetail(UUID sellerId, UUID liveId) {
        LiveSessionJpaEntity session = sessionRepository.findOwned(liveId, sellerId)
                // 타인 소유와 없는 LIVE를 같은 404로 응답한다(security.md S10, LiveSettingsService와 동일 이유).
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        if (session.getStatus() != LiveStatus.LIVE) {
            return LiveDetailResponse.from(session, null, null);
        }
        Integer viewerCount = channelRepository.findById(session.getChannelId())
                .map(LiveChannelJpaEntity::getIvsChannelArn)
                .map(ivsClient::getViewerCount)
                .orElse(null);
        long elapsedSeconds = Duration.between(session.getActualStartAt(), Instant.now()).getSeconds();
        return LiveDetailResponse.from(session, viewerCount, elapsedSeconds);
    }

    /** 스튜디오 상태 탭 배지용 건수(FE 요청). */
    public LiveStatusCountsResponse countMineByStatus(UUID sellerId) {
        long draft = 0, scheduled = 0, live = 0, ended = 0, error = 0;
        for (LiveStatusCountProjection row : sessionRepository.countBySellerIdGroupByStatus(sellerId)) {
            switch (LiveStatus.valueOf(row.getStatus())) {
                case DRAFT -> draft = row.getCount();
                case SCHEDULED -> scheduled = row.getCount();
                case LIVE -> live = row.getCount();
                case ENDED -> ended = row.getCount();
                case ERROR -> error = row.getCount();
            }
        }
        return new LiveStatusCountsResponse(draft, scheduled, live, ended, error);
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

        // offset(long)을 ranked.size()(int)와 먼저 비교한 뒤에만 int로 좁힌다 — 순서를
        // 바꾸면 offset이 Integer.MAX_VALUE를 넘는 극단적인 page*size 조합에서 캐스팅이
        // 음수로 랩어라운드돼 subList가 IndexOutOfBoundsException을 던진다(리뷰 지적).
        long offset = pageable.getOffset();
        if (offset >= ranked.size()) {
            return new PageImpl<>(List.of(), pageable, ranked.size());
        }
        int start = (int) offset;
        int end = Math.min(start + pageable.getPageSize(), ranked.size());
        return new PageImpl<>(ranked.subList(start, end), pageable, ranked.size());
    }

    /** 홈 배너(요구사항정의서 10.1.4) — 현재 방송 중인 것만. */
    public List<LiveSessionJpaEntity> findLiveBanner() {
        return sessionRepository.findByStatusOrderByActualStartAtDesc(LiveStatus.LIVE);
    }
}
