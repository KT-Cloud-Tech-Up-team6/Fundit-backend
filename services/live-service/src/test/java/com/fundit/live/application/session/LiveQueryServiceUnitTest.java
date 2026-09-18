package com.fundit.live.application.session;

import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LiveQueryServiceUnitTest {

    @Mock private LiveSessionJpaRepository sessionRepository;
    @InjectMocks private LiveQueryService liveQueryService;

    @Test
    void 내_목록은_로그인_판매자로_조회한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        var pageable = PageRequest.of(0, 20);
        given(sessionRepository.findMine(sellerId, LiveStatus.DRAFT, pageable))
                .willReturn(new PageImpl<>(List.of()));

        // when
        liveQueryService.findMine(sellerId, LiveStatus.DRAFT, pageable);

        // then — sellerId가 쿼리에 묶여 있어야 남의 방송이 섞이지 않는다(S4)
        verify(sessionRepository).findMine(sellerId, LiveStatus.DRAFT, pageable);
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
        liveQueryService.findPublic(null, pageable);

        // then
        verify(sessionRepository).findPublic(null, pageable);
    }
}
