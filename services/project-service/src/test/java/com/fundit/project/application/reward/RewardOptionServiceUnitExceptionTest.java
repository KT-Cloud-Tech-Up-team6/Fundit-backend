package com.fundit.project.application.reward;

import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.application.port.CurrentUserProvider.CurrentUser;
import com.fundit.project.application.port.CurrentUserProvider.Role;
import com.fundit.project.application.port.FundingPort;
import com.fundit.project.application.port.InventoryPort;
import com.fundit.project.application.project.ProjectAccessGuard;
import com.fundit.project.domain.ProjectErrorCode;
import com.fundit.project.domain.reward.RewardOption;
import com.fundit.project.domain.reward.RewardOptionRepository;
import com.fundit.project.domain.reward.RewardRepository;
import com.fundit.project.fixture.ProjectFixture;
import com.fundit.project.fixture.RewardFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static com.fundit.project.support.BusinessExceptionAssertions.assertBusinessException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("RewardOptionService 예외")
@ExtendWith(MockitoExtension.class)
class RewardOptionServiceUnitExceptionTest {

    @Mock
    private RewardRepository rewardRepository;
    @Mock
    private RewardOptionRepository rewardOptionRepository;
    @Mock
    private ProjectAccessGuard accessGuard;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private InventoryPort inventoryPort;
    @Mock
    private FundingPort fundingPort;

    @InjectMocks
    private RewardOptionService rewardOptionService;

    private CurrentUser seller;

    @BeforeEach
    void setUp() {
        seller = new CurrentUser(ProjectFixture.SELLER_ID, Role.MEMBER);
        when(currentUserProvider.require()).thenReturn(seller);
    }

    private void givenModifiableProject() {
        when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller)).thenReturn(ProjectFixture.draft());
    }

    private void givenRewardExists() {
        givenModifiableProject();
        when(rewardRepository.findActiveById(RewardFixture.REWARD_ID))
                .thenReturn(Optional.of(RewardFixture.reward()));
    }

    @Nested
    class 등록 {

        @Test
        void 초기_재고가_음수면_예외가_발생한다() {
            // given
            givenRewardExists();

            // when & then
            assertBusinessException(
                    () -> rewardOptionService.create(ProjectFixture.PUBLIC_ID, RewardFixture.REWARD_ID,
                            "화이트", RewardFixture.SKU, -1),
                    CommonErrorCode.INVALID_INPUT);
        }

        @Test
        void 초기_재고가_비어_있으면_예외가_발생한다() {
            // given
            givenRewardExists();

            // when & then
            assertBusinessException(
                    () -> rewardOptionService.create(ProjectFixture.PUBLIC_ID, RewardFixture.REWARD_ID,
                            "화이트", RewardFixture.SKU, null),
                    CommonErrorCode.INVALID_INPUT);
        }

        @Test
        void sku가_이미_쓰이고_있으면_충돌_예외가_발생한다() {
            // given
            givenRewardExists();
            when(rewardOptionRepository.existsBySku(RewardFixture.SKU)).thenReturn(true);

            // when & then
            assertBusinessException(
                    () -> rewardOptionService.create(ProjectFixture.PUBLIC_ID, RewardFixture.REWARD_ID,
                            "화이트", RewardFixture.SKU, 10),
                    ProjectErrorCode.DUPLICATE_SKU);
        }

        @Test
        void sku가_중복이면_재고_초기화를_요청하지_않는다() {
            // given
            givenRewardExists();
            when(rewardOptionRepository.existsBySku(RewardFixture.SKU)).thenReturn(true);

            // when
            assertBusinessException(
                    () -> rewardOptionService.create(ProjectFixture.PUBLIC_ID, RewardFixture.REWARD_ID,
                            "화이트", RewardFixture.SKU, 10),
                    ProjectErrorCode.DUPLICATE_SKU);

            // then
            verify(inventoryPort, never()).initialize(anyLong(), anyString(), anyInt());
        }

        @Test
        void 저장_중_유니크_위반이_나도_sku_중복으로_바꿔_던진다() {
            // given — 중복 확인과 INSERT 사이에 다른 요청이 끼어든 상황
            givenRewardExists();
            when(rewardOptionRepository.existsBySku(RewardFixture.SKU)).thenReturn(false);
            when(rewardOptionRepository.save(any(RewardOption.class)))
                    .thenThrow(new DataIntegrityViolationException("uq_reward_options_sku"));

            // when & then
            assertBusinessException(
                    () -> rewardOptionService.create(ProjectFixture.PUBLIC_ID, RewardFixture.REWARD_ID,
                            "화이트", RewardFixture.SKU, 10),
                    ProjectErrorCode.DUPLICATE_SKU);
        }

        @Test
        void 유니크_위반으로_실패하면_재고_초기화를_요청하지_않는다() {
            // given
            givenRewardExists();
            when(rewardOptionRepository.existsBySku(RewardFixture.SKU)).thenReturn(false);
            when(rewardOptionRepository.save(any(RewardOption.class)))
                    .thenThrow(new DataIntegrityViolationException("uq_reward_options_sku"));

            // when
            assertBusinessException(
                    () -> rewardOptionService.create(ProjectFixture.PUBLIC_ID, RewardFixture.REWARD_ID,
                            "화이트", RewardFixture.SKU, 10),
                    ProjectErrorCode.DUPLICATE_SKU);

            // then
            verifyNoInteractions(inventoryPort);
        }

        @Test
        void 재고_검증에_실패하면_옵션을_저장하지_않는다() {
            // given
            givenRewardExists();

            // when
            assertBusinessException(
                    () -> rewardOptionService.create(ProjectFixture.PUBLIC_ID, RewardFixture.REWARD_ID,
                            "화이트", RewardFixture.SKU, -1),
                    CommonErrorCode.INVALID_INPUT);

            // then
            verify(rewardOptionRepository, never()).save(any(RewardOption.class));
        }
    }

    @Nested
    class 리워드_확인 {

        @Test
        void 리워드가_없으면_없음_예외가_발생한다() {
            // given
            givenModifiableProject();
            when(rewardRepository.findActiveById(RewardFixture.REWARD_ID)).thenReturn(Optional.empty());

            // when & then
            assertBusinessException(
                    () -> rewardOptionService.create(ProjectFixture.PUBLIC_ID, RewardFixture.REWARD_ID,
                            "화이트", RewardFixture.SKU, 10),
                    CommonErrorCode.NOT_FOUND);
        }
    }

    @Nested
    class 옵션_확인 {

        @Test
        void 옵션이_없으면_없음_예외가_발생한다() {
            // given
            givenRewardExists();
            when(rewardOptionRepository.findActiveById(RewardFixture.OPTION_ID)).thenReturn(Optional.empty());

            // when & then
            assertBusinessException(
                    () -> rewardOptionService.rename(ProjectFixture.PUBLIC_ID, RewardFixture.REWARD_ID,
                            RewardFixture.OPTION_ID, "블랙"),
                    CommonErrorCode.NOT_FOUND);
        }

        @Test
        void 다른_리워드의_옵션이면_없음_예외가_발생한다() {
            // given
            givenRewardExists();
            when(rewardOptionRepository.findActiveById(RewardFixture.OPTION_ID))
                    .thenReturn(Optional.of(RewardFixture.optionBase().rewardId(999L).build()));

            // when & then
            assertBusinessException(
                    () -> rewardOptionService.rename(ProjectFixture.PUBLIC_ID, RewardFixture.REWARD_ID,
                            RewardFixture.OPTION_ID, "블랙"),
                    CommonErrorCode.NOT_FOUND);
        }
    }

    @Nested
    class 삭제 {

        @Test
        void 펀딩_참여_이력이_있으면_예외가_발생한다() {
            // given
            givenRewardExists();
            when(rewardOptionRepository.findActiveById(RewardFixture.OPTION_ID))
                    .thenReturn(Optional.of(RewardFixture.option()));
            when(fundingPort.hasFundingForOption(RewardFixture.OPTION_ID)).thenReturn(true);

            // when & then
            assertBusinessException(
                    () -> rewardOptionService.delete(ProjectFixture.PUBLIC_ID, RewardFixture.REWARD_ID,
                            RewardFixture.OPTION_ID),
                    ProjectErrorCode.REWARD_HAS_ACTIVE_FUNDING);
        }

        @Test
        void 삭제가_막히면_재고_비활성화도_요청하지_않는다() {
            // given
            givenRewardExists();
            when(rewardOptionRepository.findActiveById(RewardFixture.OPTION_ID))
                    .thenReturn(Optional.of(RewardFixture.option()));
            when(fundingPort.hasFundingForOption(RewardFixture.OPTION_ID)).thenReturn(true);

            // when
            assertBusinessException(
                    () -> rewardOptionService.delete(ProjectFixture.PUBLIC_ID, RewardFixture.REWARD_ID,
                            RewardFixture.OPTION_ID),
                    ProjectErrorCode.REWARD_HAS_ACTIVE_FUNDING);

            // then
            verifyNoInteractions(inventoryPort);
        }
    }

    @Nested
    class 검수_중_잠금 {

        @Test
        void 옵션을_등록하려_하면_잠김_예외가_발생한다() {
            // given
            when(accessGuard.findOwned(ProjectFixture.PUBLIC_ID, seller))
                    .thenReturn(ProjectFixture.pendingReview());

            // when & then
            assertBusinessException(
                    () -> rewardOptionService.create(ProjectFixture.PUBLIC_ID, RewardFixture.REWARD_ID,
                            "화이트", RewardFixture.SKU, 10),
                    CommonErrorCode.RESOURCE_LOCKED);
        }
    }
}
