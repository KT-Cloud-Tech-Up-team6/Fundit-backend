package com.fundit.live.infrastructure.persistence.session;

import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaEntity;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 소유권 조인과 DRAFT 제외는 JPQL에 박혀 있어 실제 DB로만 확인된다 —
 * 목킹으로는 쿼리가 맞는지 알 수 없다(test-convention.md 통합 테스트 기준).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
@Transactional
class LiveSessionJpaRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired private LiveSessionJpaRepository sessionRepository;
    @Autowired private LiveChannelJpaRepository channelRepository;

    private UUID sellerId;
    private UUID otherSellerId;
    private Long channelId;

    @BeforeEach
    void setUp() {
        sellerId = UUID.randomUUID();
        otherSellerId = UUID.randomUUID();
        channelId = seedChannel(sellerId).getId();
    }

    private LiveChannelJpaEntity seedChannel(UUID owner) {
        return channelRepository.save(LiveChannelJpaEntity.builder()
                .sellerId(owner)
                .ivsChannelArn("arn:" + owner)
                .ivsIngestEndpoint("rtmps://ingest")
                .ivsPlaybackUrl("https://play")
                .active(true)
                .build());
    }

    private LiveSessionJpaEntity seedSession(Long channel, LiveStatus status) {
        return sessionRepository.save(LiveSessionJpaEntity.builder()
                .publicId(UUID.randomUUID())
                .projectId(UUID.randomUUID())
                .channelId(channel)
                .introText("소개")
                .status(status)
                .likeCount(0)
                .build());
    }

    @Test
    void 소비자_목록에_DRAFT가_나오지_않는다() {
        // given — 상태 필터를 생략해도 새면 안 된다. 새는 순간 작성 중인 방송이 공개된다.
        seedSession(channelId, LiveStatus.DRAFT);
        seedSession(channelId, LiveStatus.LIVE);
        seedSession(channelId, LiveStatus.ENDED);

        // when
        var page = sessionRepository.findPublic(null, PageRequest.of(0, 20));

        // then
        assertThat(page.getContent()).hasSize(2)
                .extracting(LiveSessionJpaEntity::getStatus)
                .doesNotContain(LiveStatus.DRAFT);
    }

    @Test
    void 소비자_목록은_상태로_거를_수_있다() {
        // given
        seedSession(channelId, LiveStatus.LIVE);
        seedSession(channelId, LiveStatus.ENDED);

        // when
        var page = sessionRepository.findPublic(LiveStatus.LIVE, PageRequest.of(0, 20));

        // then
        assertThat(page.getContent()).hasSize(1)
                .allMatch(s -> s.getStatus() == LiveStatus.LIVE);
    }

    @Test
    void 내_목록은_DRAFT를_포함한다() {
        // given — 임시저장으로 돌아갈 유일한 경로다(PRD 6.2.4.1)
        seedSession(channelId, LiveStatus.DRAFT);

        // when
        var page = sessionRepository.findMine(sellerId, null, PageRequest.of(0, 20));

        // then
        assertThat(page.getContent()).hasSize(1)
                .allMatch(s -> s.getStatus() == LiveStatus.DRAFT);
    }

    @Test
    void 내_목록에_남의_방송이_섞이지_않는다() {
        // given
        Long otherChannel = seedChannel(otherSellerId).getId();
        seedSession(channelId, LiveStatus.LIVE);
        seedSession(otherChannel, LiveStatus.LIVE);

        // when
        var page = sessionRepository.findMine(sellerId, null, PageRequest.of(0, 20));

        // then
        assertThat(page.getContent()).hasSize(1)
                .allMatch(s -> s.getChannelId().equals(channelId));
    }

    @Test
    void 소유권_조회는_본인_것만_돌려준다() {
        // given
        LiveSessionJpaEntity mine = seedSession(channelId, LiveStatus.DRAFT);

        // when & then — 조회 자체가 소유권에 묶여 있어 "조회 후 검사"를 빠뜨릴 수 없다(S4)
        assertThat(sessionRepository.findOwned(mine.getPublicId(), sellerId)).isPresent();
        assertThat(sessionRepository.findOwned(mine.getPublicId(), otherSellerId)).isEmpty();
    }

    @Test
    void 상태를_DRAFT로_명시해도_소비자_목록에_나오지_않는다() {
        // given — 필터를 어떻게 주든 새면 안 된다. 쿼리가 status <> DRAFT를 함께 걸고 있어
        // :status = DRAFT와 동시에 만족하는 행이 없다.
        seedSession(channelId, LiveStatus.DRAFT);
        seedSession(channelId, LiveStatus.LIVE);

        // when
        var page = sessionRepository.findPublic(LiveStatus.DRAFT, PageRequest.of(0, 20));

        // then
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void 공개_단건_조회는_DRAFT를_돌려주지_않는다() {
        // given — 409로 답하면 liveId를 넣어보며 존재 여부를 캐낼 수 있다(security.md S10)
        var draft = seedSession(channelId, LiveStatus.DRAFT);
        var live = seedSession(channelId, LiveStatus.LIVE);

        // when & then
        assertThat(sessionRepository.findPublicByPublicId(draft.getPublicId())).isEmpty();
        assertThat(sessionRepository.findPublicByPublicId(live.getPublicId())).isPresent();
    }

    @Test
    void 팔로우_필터는_지정한_판매자의_방송만_돌려준다() {
        // given — "팔로우한 창작자" 필터(FE #257). 채널→판매자 조인이 실제로 맞는지 DB로 확인한다.
        Long otherChannel = seedChannel(otherSellerId).getId();
        seedSession(channelId, LiveStatus.LIVE);
        seedSession(otherChannel, LiveStatus.LIVE);

        // when
        var page = sessionRepository.findPublicBySellerIds(null, List.of(sellerId), PageRequest.of(0, 20));

        // then
        assertThat(page.getContent()).hasSize(1).allMatch(s -> s.getChannelId().equals(channelId));
    }
}
