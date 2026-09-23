package com.fundit.live.application.session;

import com.fundit.common.error.BusinessException;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LiveQueryServiceUnitTest {

    @Mock private LiveSessionJpaRepository sessionRepository;
    @Mock private LiveChannelJpaRepository channelRepository;
    @Mock private IvsClient ivsClient;
    @InjectMocks private LiveQueryService liveQueryService;

    @Test
    void 내_목록은_로그인_판매자로_조회한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        var pageable = PageRequest.of(0, 20);
        List<LiveStatus> statuses = List.of(LiveStatus.DRAFT);
        given(sessionRepository.findMine(sellerId, statuses, null, null, pageable))
                .willReturn(new PageImpl<>(List.of()));

        // when
        liveQueryService.findMine(sellerId, statuses, null, null, pageable);

        // then — sellerId가 쿼리에 묶여 있어야 남의 방송이 섞이지 않는다(S4)
        verify(sessionRepository).findMine(sellerId, statuses, null, null, pageable);
    }

    @Test
    void 내_목록에_상태_필터가_없으면_전체_상태로_채워_넘긴다() {
        // given — JPQL in은 컬렉션 파라미터가 null이면 바인딩이 실패해서 전체 목록으로 대체한다
        UUID sellerId = UUID.randomUUID();
        var pageable = PageRequest.of(0, 20);
        given(sessionRepository.findMine(any(), any(), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of()));

        // when
        liveQueryService.findMine(sellerId, null, null, null, pageable);

        // then
        verify(sessionRepository).findMine(sellerId, List.of(LiveStatus.values()), null, null, pageable);
    }

    @Test
    void 배너는_진행중인_방송만_가져온다() {
        // given
        given(sessionRepository.findByStatusOrderByActualStartAtDesc(LiveStatus.LIVE))
                .willReturn(List.of(LiveSessionJpaEntity.builder().status(LiveStatus.LIVE).build()));

        // when
        List<LiveSessionJpaEntity> banner = liveQueryService.findLiveBanner();

        // then
        assertThat(banner).hasSize(1);
        verify(sessionRepository).findByStatusOrderByActualStartAtDesc(LiveStatus.LIVE);
    }

    @Test
    void 소비자_목록은_상태_필터를_그대로_넘긴다() {
        // given — DRAFT 제외는 쿼리에 고정돼 있어 서비스가 따로 거르지 않는다
        var pageable = PageRequest.of(0, 20);
        given(sessionRepository.findPublic(null, pageable)).willReturn(new PageImpl<>(List.of()));

        // when
        liveQueryService.findPublic(null, null, null, pageable);

        // then
        verify(sessionRepository).findPublic(null, pageable);
    }

    @Test
    void sellerId가_있으면_팔로우_필터_쿼리를_쓴다() {
        // given
        var pageable = PageRequest.of(0, 20);
        List<UUID> sellerIds = List.of(UUID.randomUUID());
        given(sessionRepository.findPublicBySellerIds(null, sellerIds, pageable))
                .willReturn(new PageImpl<>(List.of()));

        // when
        liveQueryService.findPublic(null, null, sellerIds, pageable);

        // then
        verify(sessionRepository).findPublicBySellerIds(null, sellerIds, pageable);
    }

    @Test
    void sort가_viewerCount이면_LIVE만_모아_시청자수로_정렬한다() {
        // given — DB 컬럼이 아니라 IVS 조회값이라 status=LIVE로 한정해 전량 조회 후 정렬한다
        LiveSessionJpaEntity low = LiveSessionJpaEntity.builder()
                .id(1L).channelId(10L).status(LiveStatus.LIVE).build();
        LiveSessionJpaEntity high = LiveSessionJpaEntity.builder()
                .id(2L).channelId(20L).status(LiveStatus.LIVE).build();
        given(sessionRepository.findByStatusOrderByActualStartAtDesc(LiveStatus.LIVE))
                .willReturn(List.of(low, high));
        given(channelRepository.findAllById(any())).willReturn(List.of(
                LiveChannelJpaEntity.builder().id(10L).ivsChannelArn("arn-low").build(),
                LiveChannelJpaEntity.builder().id(20L).ivsChannelArn("arn-high").build()));
        given(ivsClient.getViewerCount("arn-low")).willReturn(3);
        given(ivsClient.getViewerCount("arn-high")).willReturn(30);

        // when
        Page<LiveSummaryResponse> page = liveQueryService.findPublic(
                LiveStatus.ENDED, "viewerCount", null, PageRequest.of(0, 20));

        // then — status=ENDED를 줬어도 viewerCount 정렬에선 무시되고 LIVE만 나온다
        assertThat(page.getContent()).extracting(LiveSummaryResponse::viewerCount).containsExactly(30, 3);
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void sort가_viewerCount이고_offset이_int_범위를_넘으면_빈_페이지를_돌려준다() {
        // given — page*size가 Integer.MAX_VALUE를 넘는 극단값. (int) 캐스팅을 먼저 하면
        // 음수로 랩어라운드돼 subList가 IndexOutOfBoundsException을 던진다(리뷰 지적).
        LiveSessionJpaEntity live = LiveSessionJpaEntity.builder()
                .id(1L).channelId(10L).status(LiveStatus.LIVE).build();
        given(sessionRepository.findByStatusOrderByActualStartAtDesc(LiveStatus.LIVE))
                .willReturn(List.of(live));
        given(channelRepository.findAllById(any())).willReturn(List.of(
                LiveChannelJpaEntity.builder().id(10L).ivsChannelArn("arn").build()));
        given(ivsClient.getViewerCount("arn")).willReturn(1);

        // when
        Page<LiveSummaryResponse> page = liveQueryService.findPublic(
                null, "viewerCount", null, PageRequest.of(2_000_000, 2000));

        // then
        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void 단건_상세_조회는_없는_방송이면_예외를_던진다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID liveId = UUID.randomUUID();
        given(sessionRepository.findOwned(liveId, sellerId)).willReturn(Optional.empty());

        // when & then — 타인 소유와 없는 LIVE를 같은 404로 응답한다(S10)
        assertThatThrownBy(() -> liveQueryService.findOwnedDetail(sellerId, liveId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 단건_상세_조회는_LIVE_상태면_시청자수와_경과시간을_채운다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID liveId = UUID.randomUUID();
        Instant startedAt = Instant.now().minusSeconds(90);
        LiveSessionJpaEntity session = LiveSessionJpaEntity.builder()
                .publicId(liveId).channelId(10L).status(LiveStatus.LIVE).actualStartAt(startedAt).build();
        given(sessionRepository.findOwned(liveId, sellerId)).willReturn(Optional.of(session));
        given(channelRepository.findById(10L)).willReturn(Optional.of(
                LiveChannelJpaEntity.builder().id(10L).ivsChannelArn("arn").build()));
        given(ivsClient.getViewerCount("arn")).willReturn(7);

        // when
        LiveDetailResponse detail = liveQueryService.findOwnedDetail(sellerId, liveId);

        // then
        assertThat(detail.viewerCount()).isEqualTo(7);
        assertThat(detail.elapsedSeconds()).isGreaterThanOrEqualTo(90);
    }

    @Test
    void 단건_상세_조회는_LIVE가_아니면_시청자수와_경과시간이_비어있다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID liveId = UUID.randomUUID();
        LiveSessionJpaEntity session = LiveSessionJpaEntity.builder()
                .publicId(liveId).channelId(10L).status(LiveStatus.DRAFT).build();
        given(sessionRepository.findOwned(liveId, sellerId)).willReturn(Optional.of(session));

        // when
        LiveDetailResponse detail = liveQueryService.findOwnedDetail(sellerId, liveId);

        // then
        assertThat(detail.viewerCount()).isNull();
        assertThat(detail.elapsedSeconds()).isNull();
    }

    @Test
    void 상태별_건수는_그룹핑_없이_5종_그대로_집계한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        given(sessionRepository.countBySellerIdGroupByStatus(sellerId)).willReturn(List.of(
                projectionOf("DRAFT", 2L), projectionOf("LIVE", 1L)));

        // when
        LiveStatusCountsResponse counts = liveQueryService.countMineByStatus(sellerId);

        // then
        assertThat(counts).isEqualTo(new LiveStatusCountsResponse(2, 0, 1, 0, 0));
    }

    private static LiveStatusCountProjection projectionOf(String status, Long count) {
        return new LiveStatusCountProjection() {
            @Override
            public String getStatus() {
                return status;
            }

            @Override
            public Long getCount() {
                return count;
            }
        };
    }
}
