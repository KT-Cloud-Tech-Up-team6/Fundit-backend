package com.fundit.order.infrastructure.persistence.funding;

import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingLineItem;
import com.fundit.order.domain.funding.FundingLineItemOption;
import com.fundit.order.domain.funding.FundingRepository;
import com.fundit.order.domain.funding.FundingStatus;
import com.fundit.order.domain.funding.ShippingAddress;
import com.fundit.order.domain.funding.ShippingFilter;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Funding 애그리거트(루트 + line_items + options 3개 플랫 테이블 수동 조합)의 JPA 배선을 검증한다. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
@Transactional
class FundingPersistenceAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private FundingRepository fundingRepository;
    @Autowired
    private FundingJpaRepository fundingJpaRepository;

    private Funding newFunding(UUID memberId, UUID projectId, FundingStatus status, Instant paymentExpiresAt) {
        return newFunding(memberId, projectId, status, paymentExpiresAt, "홍길동");
    }

    private Funding newFunding(UUID memberId, UUID projectId, FundingStatus status, Instant paymentExpiresAt,
                                String recipientName) {
        List<FundingLineItemOption> options = List.of(new FundingLineItemOption(null, 10L, "색상", 100L, "화이트"));
        List<FundingLineItem> lineItems = List.of(new FundingLineItem(null, 5L, "얼리버드 패키지", 2, 10_000L, options));
        Funding funding = Funding.create(memberId, projectId, "프로젝트",
                new ShippingAddress(recipientName, "010-1234-5678", "12345", "서울시", "101동"),
                3_000L, lineItems, paymentExpiresAt, null, null);
        if (status != FundingStatus.PENDING) {
            funding = funding.toBuilder().status(status).build();
        }
        return funding;
    }

    @Test
    void 생성하면_라인아이템과_옵션이_함께_저장된다() {
        // given
        UUID memberId = UUID.randomUUID();
        Funding funding = newFunding(memberId, UUID.randomUUID(), FundingStatus.PENDING, Instant.now().plusSeconds(1800));

        // when
        Funding saved = fundingRepository.save(funding);

        // then
        Funding found = fundingRepository.findByPublicId(saved.getPublicId()).orElseThrow();
        assertThat(found.getLineItems()).hasSize(1);
        FundingLineItem lineItem = found.getLineItems().get(0);
        assertThat(lineItem.rewardName()).isEqualTo("얼리버드 패키지");
        assertThat(lineItem.options()).singleElement()
                .extracting(FundingLineItemOption::optionValue).isEqualTo("화이트");
    }

    @Test
    void 상태만_바꿔_다시_저장해도_라인아이템이_중복_적재되지_않는다() {
        // given
        UUID memberId = UUID.randomUUID();
        Funding saved = fundingRepository.save(newFunding(memberId, UUID.randomUUID(), FundingStatus.PENDING, Instant.now().plusSeconds(1800)));
        Funding loaded = fundingRepository.findByPublicId(saved.getPublicId()).orElseThrow();

        // when
        loaded.cancelByMember();
        fundingRepository.save(loaded);

        // then
        Funding reloaded = fundingRepository.findByPublicId(saved.getPublicId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(FundingStatus.CANCELLED_BY_MEMBER);
        assertThat(reloaded.getLineItems()).hasSize(1);
    }

    @Test
    void 회원ID로_페이징_조회한다() {
        // given
        UUID memberId = UUID.randomUUID();
        fundingRepository.save(newFunding(memberId, UUID.randomUUID(), FundingStatus.PENDING, Instant.now().plusSeconds(1800)));
        fundingRepository.save(newFunding(memberId, UUID.randomUUID(), FundingStatus.PENDING, Instant.now().plusSeconds(1800)));
        fundingRepository.save(newFunding(UUID.randomUUID(), UUID.randomUUID(), FundingStatus.PENDING, Instant.now().plusSeconds(1800)));

        // when
        var page = fundingRepository.findByMemberId(memberId, null, PageRequest.of(0, 20));

        // then
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void 상태필터를_지정하면_해당_상태만_조회한다() {
        // given
        UUID memberId = UUID.randomUUID();
        fundingRepository.save(newFunding(memberId, UUID.randomUUID(), FundingStatus.PENDING, Instant.now().plusSeconds(1800)));
        fundingRepository.save(newFunding(memberId, UUID.randomUUID(), FundingStatus.CANCELLED_BY_MEMBER, Instant.now().plusSeconds(1800)));

        // when
        var page = fundingRepository.findByMemberId(memberId, FundingStatus.CANCELLED_BY_MEMBER, PageRequest.of(0, 20));

        // then
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getStatus()).isEqualTo(FundingStatus.CANCELLED_BY_MEMBER);
    }

    @Test
    void 결제만료시각이_지난_PENDING_건만_조회한다() {
        // given
        UUID expiredProjectId = UUID.randomUUID();
        fundingRepository.save(newFunding(UUID.randomUUID(), expiredProjectId, FundingStatus.PENDING, Instant.now().minusSeconds(60)));
        fundingRepository.save(newFunding(UUID.randomUUID(), UUID.randomUUID(), FundingStatus.PENDING, Instant.now().plusSeconds(1800)));
        fundingRepository.save(newFunding(UUID.randomUUID(), UUID.randomUUID(), FundingStatus.CANCELLED_BY_MEMBER, Instant.now().minusSeconds(60)));

        // when
        List<Funding> targets = fundingRepository.findPendingExpiredBefore(Instant.now());

        // then
        assertThat(targets).hasSize(1);
        assertThat(targets.get(0).getProjectId()).isEqualTo(expiredProjectId);
    }

    /**
     * findActiveByProjectId는 project_public_id(UUID) 기준으로 조회한다.
     * 레거시 project_id만 있고 UUID가 없는 행은 백필 전에 이 조회에 잡히지 않는다.
     */
    @Test
    void 프로젝트의_활성_참여건만_조회한다() {
        // given
        UUID projectPublicId = UUID.randomUUID();
        fundingRepository.save(newFunding(UUID.randomUUID(), projectPublicId, FundingStatus.PENDING, Instant.now().plusSeconds(1800)));
        fundingRepository.save(newFunding(UUID.randomUUID(), projectPublicId, FundingStatus.CANCELLED_BY_MEMBER, Instant.now().plusSeconds(1800)));
        fundingRepository.save(newFunding(UUID.randomUUID(), UUID.randomUUID(), FundingStatus.PENDING, Instant.now().plusSeconds(1800)));

        // when
        List<Funding> active = fundingRepository.findActiveByProjectId(projectPublicId);

        // then
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getStatus()).isEqualTo(FundingStatus.PENDING);
        assertThat(active.get(0).getProjectId()).isEqualTo(projectPublicId);
    }

    /**
     * #129 — q 없이 판매자 발송목록을 조회해도 500(bug/project-inquire#127과 동일한
     * PostgreSQL bytea 타입추론 함정)이 재현되지 않는지, GOAL_ACHIEVED 건만 걸러지는지 검증한다.
     */
    @Test
    void q가_없어도_500이_아니고_성립건만_조회된다() {
        // given
        UUID projectId = UUID.randomUUID();
        fundingRepository.save(newFunding(UUID.randomUUID(), projectId, FundingStatus.GOAL_ACHIEVED, Instant.now()));
        fundingRepository.save(newFunding(UUID.randomUUID(), projectId, FundingStatus.PENDING, Instant.now().plusSeconds(1800)));

        // when
        var page = fundingRepository.findSellerOrders(projectId, ShippingFilter.ALL, null, PageRequest.of(0, 20));

        // then
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getStatus()).isEqualTo(FundingStatus.GOAL_ACHIEVED);
    }

    @Test
    void q로_수령인명이_검색된다() {
        // given
        UUID projectId = UUID.randomUUID();
        fundingRepository.save(newFunding(UUID.randomUUID(), projectId, FundingStatus.GOAL_ACHIEVED, Instant.now(), "우산연구소"));
        fundingRepository.save(newFunding(UUID.randomUUID(), projectId, FundingStatus.GOAL_ACHIEVED, Instant.now(), "텀블러클럽"));

        // when
        var page = fundingRepository.findSellerOrders(projectId, ShippingFilter.ALL, "우산", PageRequest.of(0, 20));

        // then
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getShippingAddress().recipientName()).isEqualTo("우산연구소");
    }

    @Test
    void q로_주문번호_일부가_검색된다() {
        // given
        UUID projectId = UUID.randomUUID();
        Funding saved = fundingRepository.save(newFunding(UUID.randomUUID(), projectId, FundingStatus.GOAL_ACHIEVED, Instant.now()));
        fundingRepository.save(newFunding(UUID.randomUUID(), projectId, FundingStatus.GOAL_ACHIEVED, Instant.now()));
        String keyword = saved.getPublicId().toString().substring(0, 8);

        // when
        var page = fundingRepository.findSellerOrders(projectId, ShippingFilter.ALL, keyword, PageRequest.of(0, 20));

        // then
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getPublicId()).isEqualTo(saved.getPublicId());
    }

    /** 검색어의 `%`/`_`가 LIKE 와일드카드로 해석되면 안 되고 리터럴로 매칭돼야 한다(코드리뷰 지적). */
    @Test
    void q에_포함된_LIKE_와일드카드는_리터럴로만_매칭된다() {
        // given
        UUID projectId = UUID.randomUUID();
        fundingRepository.save(newFunding(UUID.randomUUID(), projectId, FundingStatus.GOAL_ACHIEVED, Instant.now(), "50% 할인단"));
        fundingRepository.save(newFunding(UUID.randomUUID(), projectId, FundingStatus.GOAL_ACHIEVED, Instant.now(), "홍길동"));

        // when — "%"를 와일드카드로 해석하면 두 건 다 매칭된다
        var page = fundingRepository.findSellerOrders(projectId, ShippingFilter.ALL, "50%", PageRequest.of(0, 20));

        // then
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getShippingAddress().recipientName()).isEqualTo("50% 할인단");
    }

    /**
     * shippedAt은 markShipped(조건부 UPDATE)로만 채워지고, 이후 애그리거트를 다시
     * hydrate→save해도(예: PaymentEventSyncService 등 다른 흐름) 지워지면 안 된다(코드리뷰 지적).
     */
    @Test
    void 발송완료_후_다른_사유로_재저장해도_shippedAt이_유지된다() {
        // given
        Funding saved = fundingRepository.save(newFunding(UUID.randomUUID(), UUID.randomUUID(), FundingStatus.GOAL_ACHIEVED, Instant.now()));
        fundingRepository.markShipped(saved.getPublicId(), Instant.now());

        // when — 발송과 무관한 흐름이 같은 애그리거트를 다시 hydrate해서 저장
        Funding reloaded = fundingRepository.findByPublicId(saved.getPublicId()).orElseThrow();
        fundingRepository.save(reloaded);

        // then
        Instant persisted = fundingJpaRepository.findByPublicId(saved.getPublicId()).orElseThrow().getShippedAt();
        assertThat(persisted).isNotNull();
    }

    @Test
    void 발송상태_필터로_대기와_완료가_구분된다() {
        // given
        UUID projectId = UUID.randomUUID();
        Funding waiting = fundingRepository.save(newFunding(UUID.randomUUID(), projectId, FundingStatus.GOAL_ACHIEVED, Instant.now()));
        Funding shipped = fundingRepository.save(newFunding(UUID.randomUUID(), projectId, FundingStatus.GOAL_ACHIEVED, Instant.now()));
        fundingRepository.markShipped(shipped.getPublicId(), Instant.now());

        // when
        var waitingPage = fundingRepository.findSellerOrders(projectId, ShippingFilter.WAITING, null, PageRequest.of(0, 20));
        var shippedPage = fundingRepository.findSellerOrders(projectId, ShippingFilter.SHIPPED, null, PageRequest.of(0, 20));

        // then
        assertThat(waitingPage.getContent()).extracting(Funding::getPublicId).containsExactly(waiting.getPublicId());
        assertThat(shippedPage.getContent()).extracting(Funding::getPublicId).containsExactly(shipped.getPublicId());
    }

    @Test
    void 발송상태별_건수를_집계한다() {
        // given
        UUID projectId = UUID.randomUUID();
        Funding shipped = fundingRepository.save(newFunding(UUID.randomUUID(), projectId, FundingStatus.GOAL_ACHIEVED, Instant.now()));
        fundingRepository.save(newFunding(UUID.randomUUID(), projectId, FundingStatus.GOAL_ACHIEVED, Instant.now()));
        fundingRepository.save(newFunding(UUID.randomUUID(), projectId, FundingStatus.PENDING, Instant.now().plusSeconds(1800)));
        fundingRepository.markShipped(shipped.getPublicId(), Instant.now());

        // when
        var counts = fundingRepository.countSellerOrdersByShippingStatus(projectId);

        // then
        assertThat(counts.waiting()).isEqualTo(1);
        assertThat(counts.shipped()).isEqualTo(1);
    }

    @Test
    void markShipped는_이미_채워졌으면_먼저_기록된_시각을_유지한다() {
        // given
        Funding saved = fundingRepository.save(newFunding(UUID.randomUUID(), UUID.randomUUID(), FundingStatus.GOAL_ACHIEVED, Instant.now()));
        Instant first = Instant.now().minusSeconds(60);
        fundingRepository.markShipped(saved.getPublicId(), first);

        // when — 중복 수신(Kafka at-least-once) 상황을 재현
        fundingRepository.markShipped(saved.getPublicId(), Instant.now());

        // then
        Instant persisted = fundingJpaRepository.findByPublicId(saved.getPublicId()).orElseThrow().getShippedAt();
        assertThat(persisted).isCloseTo(first, org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.SECONDS));
    }
}
