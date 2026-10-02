package com.fundit.project.infrastructure.seed;

import com.fundit.project.application.project.ProjectIndexEventPublisher;
import com.fundit.project.application.project.ProjectIndexEventPublisher.ProjectIndexedEvent;
import com.fundit.project.application.project.SellerProfileClient;
import com.fundit.project.infrastructure.persistence.fundingstatus.FundingStatusSnapshotJpaRepository;
import com.fundit.project.infrastructure.persistence.project.ProjectJpaEntity;
import com.fundit.project.infrastructure.persistence.project.ProjectJpaRepository;
import com.fundit.project.infrastructure.persistence.reward.RewardJpaEntity;
import com.fundit.project.infrastructure.persistence.reward.RewardJpaRepository;
import com.fundit.project.infrastructure.persistence.reward.RewardOptionGroupJpaEntity;
import com.fundit.project.infrastructure.persistence.reward.RewardOptionGroupJpaRepository;
import com.fundit.project.infrastructure.persistence.reward.RewardOptionValueJpaEntity;
import com.fundit.project.infrastructure.persistence.reward.RewardOptionValueJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ClinpotCloneProjectSeederUnitTest {

    private static final Instant BASE = Instant.parse("2026-10-02T00:00:00Z");
    private static final UUID SELLER_ID = UUID.fromString("01a0f9ff-2d11-722d-b68e-6b074bfb108a");

    @Mock private ProjectJpaRepository projectRepository;
    @Mock private FundingStatusSnapshotJpaRepository snapshotRepository;
    @Mock private RewardJpaRepository rewardRepository;
    @Mock private RewardOptionGroupJpaRepository optionGroupRepository;
    @Mock private RewardOptionValueJpaRepository optionValueRepository;
    @Mock private SellerProfileClient sellerProfileClient;
    @Mock private ProjectIndexEventPublisher indexEventPublisher;
    @Mock private TransactionTemplate transactionTemplate;

    private ClinpotCloneProjectSeeder seeder() {
        return new ClinpotCloneProjectSeeder(projectRepository, snapshotRepository, rewardRepository,
                optionGroupRepository, optionValueRepository, sellerProfileClient, indexEventPublisher,
                transactionTemplate);
    }

    @SuppressWarnings("unchecked")
    private void givenTransactionRuns() {
        willAnswer(inv -> {
            ((Consumer<TransactionStatus>) inv.getArgument(0)).accept(null);
            return null;
        }).given(transactionTemplate).executeWithoutResult(any());
    }

    private static ProjectJpaEntity source() {
        return ProjectJpaEntity.builder()
                .id(1L)
                .publicId(ClinpotCloneProjectSeeder.SOURCE_PROJECT_ID)
                .sellerId(SELLER_ID)
                .categoryMajor("테크·가전")
                .categoryMinor("주방가전")
                .title("[신제품 최초 공개] 음식물 냄새를 한 번에, 클린팟 미니 음식물처리기")
                .goalAmount(20_000_000L)
                .coverImageUrl("https://infrastudy.store/media/clinpot.png")
                .status("ONGOING")
                .build();
    }

    @Test
    void 클론이_없고_원본이_있으면_원본을_복제해_성립된_프로젝트로_넣는다() {
        // given
        given(projectRepository.existsByPublicId(ClinpotCloneProjectSeeder.CLONE_PROJECT_ID)).willReturn(false);
        given(projectRepository.findByPublicIdAndDeletedAtIsNull(ClinpotCloneProjectSeeder.SOURCE_PROJECT_ID))
                .willReturn(Optional.of(source()));
        given(rewardRepository.findByProjectIdAndDeletedAtIsNullOrderBySortOrderAsc(1L)).willReturn(List.of(
                RewardJpaEntity.builder().id(27L).name("기본 패키지").price(350_000L).isLimited(false).build()));
        given(optionGroupRepository.findByRewardIdAndDeletedAtIsNullOrderBySortOrderAsc(27L)).willReturn(List.of());
        given(sellerProfileClient.getDisplayName(SELLER_ID)).willReturn(Optional.of("클린팟"));
        given(projectRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        givenTransactionRuns();

        // when
        boolean created = seeder().seed(BASE);

        // then
        assertThat(created).isTrue();
        ArgumentCaptor<ProjectJpaEntity> project = ArgumentCaptor.forClass(ProjectJpaEntity.class);
        verify(projectRepository).save(project.capture());
        ProjectJpaEntity clone = project.getValue();
        assertThat(clone.getPublicId()).isEqualTo(ClinpotCloneProjectSeeder.CLONE_PROJECT_ID);
        assertThat(clone.getSellerId()).isEqualTo(SELLER_ID);
        assertThat(clone.getTitle()).isEqualTo(source().getTitle());
        assertThat(clone.getCoverImageUrl()).isEqualTo(source().getCoverImageUrl());
        // 원본 목표(2,000만원)를 쓰면 시드 펀딩 31건으로 "성공인데 46%"가 된다
        assertThat(clone.getGoalAmount()).isEqualTo(ClinpotCloneProjectSeeder.CLONE_GOAL_AMOUNT);
        // SUCCEEDED + 마감 통보 시각이 있어야 마감 감시(ONGOING만)에 다시 걸리지 않는다
        assertThat(clone.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(clone.getFundingDeadline()).isEqualTo(BASE.minus(Duration.ofDays(15)));
        assertThat(clone.getDeadlineNotifiedAt()).isNotNull();
        verify(snapshotRepository).save(any());

        ArgumentCaptor<ProjectIndexedEvent> event = ArgumentCaptor.forClass(ProjectIndexedEvent.class);
        verify(indexEventPublisher).publishProjectApproved(event.capture());
        assertThat(event.getValue().sellerDisplayName()).isEqualTo("클린팟");
    }

    @Test
    void 원본에_한정_리워드와_옵션이_있으면_무제한으로_복제한다() {
        // given
        given(projectRepository.existsByPublicId(ClinpotCloneProjectSeeder.CLONE_PROJECT_ID)).willReturn(false);
        given(projectRepository.findByPublicIdAndDeletedAtIsNull(ClinpotCloneProjectSeeder.SOURCE_PROJECT_ID))
                .willReturn(Optional.of(source()));
        given(sellerProfileClient.getDisplayName(SELLER_ID)).willReturn(Optional.empty());
        given(projectRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(rewardRepository.findByProjectIdAndDeletedAtIsNullOrderBySortOrderAsc(1L)).willReturn(List.of(
                RewardJpaEntity.builder().id(26L).name("얼리버드").price(300_000L).isLimited(true).quantity(50)
                        .isEarlyBird(true).build(),
                RewardJpaEntity.builder().id(27L).name("기본 패키지").price(350_000L).isLimited(false).build()));
        given(rewardRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(optionGroupRepository.findByRewardIdAndDeletedAtIsNullOrderBySortOrderAsc(26L)).willReturn(List.of(
                RewardOptionGroupJpaEntity.builder().id(12L).name("색상").sortOrder(0).build()));
        given(optionGroupRepository.findByRewardIdAndDeletedAtIsNullOrderBySortOrderAsc(27L)).willReturn(List.of());
        given(optionGroupRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(optionValueRepository.findByOptionGroupIdOrderBySortOrderAsc(12L)).willReturn(List.of(
                RewardOptionValueJpaEntity.builder().value("퓨어 화이트").sortOrder(0).build(),
                RewardOptionValueJpaEntity.builder().value("단종 색상").sortOrder(1).deletedAt(BASE).build()));
        givenTransactionRuns();

        // when
        seeder().seed(BASE);

        // then
        ArgumentCaptor<RewardJpaEntity> rewards = ArgumentCaptor.forClass(RewardJpaEntity.class);
        verify(rewardRepository, times(2)).save(rewards.capture());
        assertThat(rewards.getAllValues()).extracting(RewardJpaEntity::getName).containsExactly("얼리버드", "기본 패키지");
        // 한정 리워드는 order 재고 행이 필요한데 리워드 생성 이벤트를 내지 않는다 — 무제한으로 복제한다
        assertThat(rewards.getAllValues()).allMatch(r -> !r.getIsLimited());
        assertThat(rewards.getAllValues().getFirst().getIsEarlyBird()).isTrue();

        verify(optionGroupRepository).save(any());
        // 지운 옵션 값은 복제하지 않는다
        ArgumentCaptor<RewardOptionValueJpaEntity> values = ArgumentCaptor.forClass(RewardOptionValueJpaEntity.class);
        verify(optionValueRepository).save(values.capture());
        assertThat(values.getValue().getValue()).isEqualTo("퓨어 화이트");
    }

    @Test
    void 클론이_이미_있으면_건너뛴다() {
        // given
        given(projectRepository.existsByPublicId(ClinpotCloneProjectSeeder.CLONE_PROJECT_ID)).willReturn(true);

        // when
        boolean created = seeder().seed(BASE);

        // then
        assertThat(created).isFalse();
        verify(projectRepository, never()).save(any());
        verifyNoInteractions(transactionTemplate, indexEventPublisher);
    }
}
