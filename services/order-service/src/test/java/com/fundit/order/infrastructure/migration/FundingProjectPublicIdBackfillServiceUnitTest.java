package com.fundit.order.infrastructure.migration;

import com.fundit.order.application.catalog.ProjectOwnershipClient;
import com.fundit.order.infrastructure.persistence.funding.FundingJpaEntity;
import com.fundit.order.infrastructure.persistence.funding.FundingJpaRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundingProjectPublicIdBackfillServiceUnitTest {

    @Mock
    private FundingJpaRepository fundingJpaRepository;
    @Mock
    private ProjectOwnershipClient projectOwnershipClient;

    @InjectMocks
    private FundingProjectPublicIdBackfillService backfillService;

    private FundingJpaEntity legacyEntity(Long id, Long projectId, UUID projectPublicId) {
        return FundingJpaEntity.builder().id(id).projectId(projectId).projectPublicId(projectPublicId).build();
    }

    @Nested
    class 대상_조회 {

        @Test
        void 레거시_projectId만_있고_UUID가_없는_행만_반환한다() {
            // given
            when(fundingJpaRepository.findByProjectIdIsNotNullAndProjectPublicIdIsNull())
                    .thenReturn(List.of(legacyEntity(1L, 7L, null), legacyEntity(2L, 8L, null)));

            // when
            var ids = backfillService.findTargetFundingIds();

            // then
            assertThat(ids).containsExactly(1L, 2L);
        }
    }

    @Nested
    class 한_건_백필 {

        @Test
        void publicId를_찾으면_채우고_true를_반환한다() {
            // given
            UUID publicId = UUID.randomUUID();
            when(fundingJpaRepository.findById(1L)).thenReturn(Optional.of(legacyEntity(1L, 7L, null)));
            when(projectOwnershipClient.findPublicId(7L)).thenReturn(Optional.of(publicId));

            // when
            boolean result = backfillService.backfillOne(1L);

            // then
            assertThat(result).isTrue();
            verify(fundingJpaRepository).updateProjectPublicId(1L, publicId);
        }

        @Test
        void publicId를_못_찾으면_갱신하지_않고_false를_반환한다() {
            // given
            when(fundingJpaRepository.findById(1L)).thenReturn(Optional.of(legacyEntity(1L, 7L, null)));
            when(projectOwnershipClient.findPublicId(7L)).thenReturn(Optional.empty());

            // when
            boolean result = backfillService.backfillOne(1L);

            // then
            assertThat(result).isFalse();
            verify(fundingJpaRepository, never()).updateProjectPublicId(any(), any());
        }

        @Test
        void 이미_채워진_행이면_재실행해도_건너뛴다() {
            // given — idempotent 재실행
            when(fundingJpaRepository.findById(1L))
                    .thenReturn(Optional.of(legacyEntity(1L, 7L, UUID.randomUUID())));

            // when
            boolean result = backfillService.backfillOne(1L);

            // then
            assertThat(result).isFalse();
            verify(fundingJpaRepository, never()).updateProjectPublicId(any(), any());
        }

        @Test
        void 존재하지_않는_펀딩이면_false를_반환한다() {
            // given
            when(fundingJpaRepository.findById(999L)).thenReturn(Optional.empty());

            // when
            boolean result = backfillService.backfillOne(999L);

            // then
            assertThat(result).isFalse();
        }
    }
}
