package com.fundit.project.application.reward;

import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.domain.reward.Reward;
import com.fundit.project.domain.reward.RewardOptionGroup;
import com.fundit.project.domain.reward.RewardRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RewardServiceUnitTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private RewardRepository rewardRepository;
    @Mock
    private RewardEventPublisher rewardEventPublisher;

    @InjectMocks
    private RewardService rewardService;

    private Project ownedProject(UUID sellerId, UUID publicId) {
        return Project.builder()
                .id(1L).publicId(publicId).sellerId(sellerId).status(ProjectStatus.DRAFT)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    @Nested
    class 등록 {

        @Test
        void 옵션이_있으면_옵션도_함께_저장하고_이벤트를_발행한다() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID projectPublicId = UUID.randomUUID();
            Project project = ownedProject(sellerId, projectPublicId);
            List<RewardOptionGroup> options = List.of(new RewardOptionGroup("색상", List.of("화이트")));
            when(projectRepository.findByPublicId(projectPublicId)).thenReturn(Optional.of(project));
            when(rewardRepository.save(any())).thenAnswer(invocation -> {
                Reward r = invocation.getArgument(0);
                return r.toBuilder().id(10L).build();
            });

            // when
            Reward result = rewardService.create(sellerId, projectPublicId, new RewardService.CreateRewardCommand(
                    "얼리버드", "설명", null, 39000L, true, 100, true, options));

            // then
            assertThat(result.getId()).isEqualTo(10L);
            verify(rewardRepository).replaceOptions(eq(10L), eq(options));
            verify(rewardEventPublisher).publishRewardCreated(any());
        }

        @Test
        void 옵션이_없으면_옵션저장을_호출하지_않는다() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID projectPublicId = UUID.randomUUID();
            Project project = ownedProject(sellerId, projectPublicId);
            when(projectRepository.findByPublicId(projectPublicId)).thenReturn(Optional.of(project));
            when(rewardRepository.save(any())).thenAnswer(invocation -> {
                Reward r = invocation.getArgument(0);
                return r.toBuilder().id(10L).build();
            });

            // when
            rewardService.create(sellerId, projectPublicId, new RewardService.CreateRewardCommand(
                    "얼리버드", "설명", null, 39000L, false, null, false, null));

            // then
            verify(rewardRepository, never()).replaceOptions(any(), any());
        }
    }

    @Nested
    class 수정 {

        @Test
        void 전달된_필드만_병합해서_저장한다() {
            // given
            UUID sellerId = UUID.randomUUID();
            Reward existing = Reward.create(1L, "기존이름", "기존설명", null, 10000L, false, null, false, null)
                    .toBuilder().id(5L).build();
            Project project = ownedProject(sellerId, UUID.randomUUID());
            when(rewardRepository.findById(5L)).thenReturn(Optional.of(existing));
            when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
            when(rewardRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            Reward result = rewardService.update(sellerId, 5L, new RewardService.UpdateRewardCommand(
                    "새이름", null, null, null, null, null, null, null));

            // then
            assertThat(result.getName()).isEqualTo("새이름");
            assertThat(result.getDescription()).isEqualTo("기존설명");
        }
    }

    @Test
    void 삭제하면_소프트삭제된다() {
        // given
        UUID sellerId = UUID.randomUUID();
        Reward existing = Reward.create(1L, "이름", "설명", null, 10000L, false, null, false, null)
                .toBuilder().id(5L).build();
        Project project = ownedProject(sellerId, UUID.randomUUID());
        when(rewardRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(rewardRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        rewardService.delete(sellerId, 5L);

        // then
        ArgumentCaptor<Reward> captor = ArgumentCaptor.forClass(Reward.class);
        verify(rewardRepository).save(captor.capture());
        assertThat(captor.getValue().isDeleted()).isTrue();
    }

    @Test
    void 고시정보를_저장한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        Reward existing = Reward.create(1L, "이름", "설명", null, 10000L, false, null, false, null)
                .toBuilder().id(5L).build();
        Project project = ownedProject(sellerId, UUID.randomUUID());
        when(rewardRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(rewardRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Reward result = rewardService.updateDisclosure(sellerId, 5L, "COSMETIC", java.util.Map.of("제조국", "대한민국"));

        // then
        assertThat(result.getCategoryType()).isEqualTo("COSMETIC");
    }

    @Test
    void 환불정책_특이사항을_저장한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        Reward existing = Reward.create(1L, "이름", "설명", null, 10000L, false, null, false, null)
                .toBuilder().id(5L).build();
        Project project = ownedProject(sellerId, UUID.randomUUID());
        when(rewardRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(rewardRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Reward result = rewardService.updateRefundPolicy(sellerId, 5L, true);

        // then
        assertThat(result.isSimpleRefundDisabled()).isTrue();
    }
}
