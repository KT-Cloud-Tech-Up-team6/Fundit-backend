package com.fundit.project.infrastructure.seed;

import com.fundit.project.application.project.ProjectIndexEventPublisher;
import com.fundit.project.application.project.SellerProfileClient;
import com.fundit.project.infrastructure.persistence.fundingstatus.FundingStatusSnapshotJpaRepository;
import com.fundit.project.infrastructure.persistence.project.ProjectJpaEntity;
import com.fundit.project.infrastructure.persistence.project.ProjectJpaRepository;
import com.fundit.project.infrastructure.persistence.reward.RewardJpaRepository;
import com.fundit.project.infrastructure.persistence.reward.RewardOptionGroupJpaRepository;
import com.fundit.project.infrastructure.persistence.reward.RewardOptionValueJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ClinpotCloneProjectSeederUnitExceptionTest {

    private static final Instant BASE = Instant.parse("2026-10-02T00:00:00Z");

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

    @Test
    void 원본이_없으면_건너뛴다() {
        // given
        given(projectRepository.existsByPublicId(ClinpotCloneProjectSeeder.CLONE_PROJECT_ID)).willReturn(false);
        given(projectRepository.findByPublicIdAndDeletedAtIsNull(ClinpotCloneProjectSeeder.SOURCE_PROJECT_ID))
                .willReturn(Optional.empty());

        // when
        boolean created = seeder().seed(BASE);

        // then
        assertThat(created).isFalse();
        verifyNoInteractions(transactionTemplate, indexEventPublisher, sellerProfileClient);
    }

    @Test
    void 원본_리워드가_없으면_클론을_만들지_않는다() {
        // given — 만들어 버리면 다음 기동에 "이미 있음"으로 건너뛰어 리워드가 영영 안 채워진다
        given(projectRepository.existsByPublicId(ClinpotCloneProjectSeeder.CLONE_PROJECT_ID)).willReturn(false);
        given(projectRepository.findByPublicIdAndDeletedAtIsNull(ClinpotCloneProjectSeeder.SOURCE_PROJECT_ID))
                .willReturn(Optional.of(ProjectJpaEntity.builder().id(1L).build()));
        given(rewardRepository.findByProjectIdAndDeletedAtIsNullOrderBySortOrderAsc(1L)).willReturn(List.of());

        // when
        boolean created = seeder().seed(BASE);

        // then
        assertThat(created).isFalse();
        verify(projectRepository, never()).save(any());
        verifyNoInteractions(transactionTemplate, indexEventPublisher, sellerProfileClient);
    }
}
