package com.fundit.project.infrastructure.seed;

import com.fundit.project.application.project.ProjectIndexEventPublisher;
import com.fundit.project.application.project.ProjectIndexEventPublisher.ProjectIndexedEvent;
import com.fundit.project.infrastructure.persistence.fundingstatus.FundingStatusSnapshotJpaEntity;
import com.fundit.project.infrastructure.persistence.fundingstatus.FundingStatusSnapshotJpaRepository;
import com.fundit.project.infrastructure.persistence.project.ProjectJpaEntity;
import com.fundit.project.infrastructure.persistence.project.ProjectJpaRepository;
import com.fundit.project.infrastructure.persistence.reward.RewardJpaEntity;
import com.fundit.project.infrastructure.persistence.reward.RewardJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DemoFulfillmentProjectSeederUnitTest {

    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");
    private static final UUID SELLER_ID = UUID.fromString("01a0ec1e-e3f3-7063-b489-bcc235b4168f");

    @Mock private ProjectJpaRepository projectRepository;
    @Mock private FundingStatusSnapshotJpaRepository snapshotRepository;
    @Mock private RewardJpaRepository rewardRepository;
    @Mock private ProjectIndexEventPublisher indexEventPublisher;
    @Mock private TransactionTemplate transactionTemplate;

    private DemoFulfillmentProjectSeeder seeder(String sellerMemberId) {
        return new DemoFulfillmentProjectSeeder(projectRepository, snapshotRepository, rewardRepository,
                indexEventPublisher, transactionTemplate, sellerMemberId);
    }

    @SuppressWarnings("unchecked")
    private void givenTransactionRuns() {
        willAnswer(inv -> {
            ((Consumer<TransactionStatus>) inv.getArgument(0)).accept(null);
            return null;
        }).given(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    void 없으면_성립된_프로젝트와_스냅샷_무제한_리워드_색인_이벤트를_만든다() {
        // given
        given(projectRepository.existsByPublicId(DemoFulfillmentProjectSeeder.DEMO_PROJECT_ID)).willReturn(false);
        given(projectRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        givenTransactionRuns();

        // when
        boolean created = seeder(SELLER_ID.toString()).seed(BASE);

        // then — SUCCEEDED + 마감 통보 시각이 있어야 마감 감시(ONGOING만)에 다시 걸리지 않는다
        assertThat(created).isTrue();
        ArgumentCaptor<ProjectJpaEntity> project = ArgumentCaptor.forClass(ProjectJpaEntity.class);
        verify(projectRepository).save(project.capture());
        assertThat(project.getValue().getPublicId()).isEqualTo(DemoFulfillmentProjectSeeder.DEMO_PROJECT_ID);
        assertThat(project.getValue().getSellerId()).isEqualTo(SELLER_ID);
        assertThat(project.getValue().getStatus()).isEqualTo("SUCCEEDED");
        assertThat(project.getValue().getFundingDeadline()).isEqualTo(BASE.minus(Duration.ofDays(15)));
        assertThat(project.getValue().getDeadlineNotifiedAt()).isNotNull();

        ArgumentCaptor<FundingStatusSnapshotJpaEntity> snapshot = ArgumentCaptor.forClass(FundingStatusSnapshotJpaEntity.class);
        verify(snapshotRepository).save(snapshot.capture());
        assertThat(snapshot.getValue().getAchievementRate()).isEqualTo(150);

        // 무제한이라 order 재고 행이 필요 없다 — 리워드 생성 이벤트 없이 직접 넣는다
        ArgumentCaptor<RewardJpaEntity> reward = ArgumentCaptor.forClass(RewardJpaEntity.class);
        verify(rewardRepository).save(reward.capture());
        assertThat(reward.getValue().getIsLimited()).isFalse();
        assertThat(reward.getValue().getPrice()).isEqualTo(30_000L);

        verify(indexEventPublisher).publishProjectApproved(any(ProjectIndexedEvent.class));
    }

    @Test
    void 이미_있으면_건너뛴다() {
        // given — 재배포해도 중복 생성·중복 색인 이벤트가 없어야 한다
        given(projectRepository.existsByPublicId(DemoFulfillmentProjectSeeder.DEMO_PROJECT_ID)).willReturn(true);

        // when
        boolean created = seeder(SELLER_ID.toString()).seed(BASE);

        // then
        assertThat(created).isFalse();
        verifyNoInteractions(transactionTemplate, snapshotRepository, rewardRepository, indexEventPublisher);
    }

    @Test
    void 판매자_ID가_비어_있으면_건너뛴다() {
        // when
        boolean created = seeder("").seed(BASE);

        // then
        assertThat(created).isFalse();
        verifyNoInteractions(projectRepository, transactionTemplate);
    }
}
