package com.fundit.project.infrastructure.seed;

import com.fundit.project.application.project.ProjectIndexEventPublisher;
import com.fundit.project.application.project.ProjectIndexEventPublisher.ProjectIndexedEvent;
import com.fundit.project.infrastructure.persistence.fundingstatus.FundingStatusSnapshotJpaEntity;
import com.fundit.project.infrastructure.persistence.fundingstatus.FundingStatusSnapshotJpaRepository;
import com.fundit.project.infrastructure.persistence.project.ProjectJpaEntity;
import com.fundit.project.infrastructure.persistence.project.ProjectJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MockProjectSeederUnitTest {

    @Mock
    private ProjectJpaRepository projectRepository;
    @Mock
    private FundingStatusSnapshotJpaRepository snapshotRepository;
    @Mock
    private ProjectIndexEventPublisher indexEventPublisher;
    @Mock
    private TransactionTemplate transactionTemplate;

    private MockProjectSeeder seeder() {
        return new MockProjectSeeder(projectRepository, snapshotRepository, indexEventPublisher,
                transactionTemplate, JsonMapper.builder().build());
    }

    private static MockProjectSeeder.MockProject project(UUID publicId, String title) {
        return new MockProjectSeeder.MockProject(publicId, UUID.randomUUID(), "쓱쓱생활연구소", "테크·가전", "생활가전",
                title, 500_000L, Instant.parse("2026-09-08T09:00:00Z"), Instant.parse("2026-10-24T09:00:00Z"),
                new MockProjectSeeder.MockSnapshot(190_000L, 38, 9, Instant.parse("2026-09-23T09:00:00Z")));
    }

    @Test
    void 시드_파일의_프로젝트_50건을_고정_UUID로_읽는다() throws Exception {
        // when
        List<MockProjectSeeder.MockProject> projects = seeder().read();

        // then — live 목업 방송의 project_id, member 목업 판매자의 UUID와 같은 값이어야 카드에서 상세로 이어진다
        assertThat(projects).hasSize(50);
        assertThat(projects.getFirst().publicId())
                .isEqualTo(UUID.fromString("80f88d7e-089a-5226-b306-9e7168853e60"));
        assertThat(projects.getFirst().sellerId()).isEqualTo(
                UUID.nameUUIDFromBytes("fundit-mock-seller:FD-001".getBytes(StandardCharsets.UTF_8)));
        assertThat(projects).allSatisfy(p -> {
            assertThat(p.title()).isNotBlank().hasSizeLessThanOrEqualTo(40);
            assertThat(p.goalAmount()).isGreaterThanOrEqualTo(500_000L);
            assertThat(p.fundingDeadline()).isAfter(p.fundingStartAt());
            assertThat(p.snapshot()).isNotNull();
        });
    }

    @Test
    void 없는_프로젝트만_스냅샷과_색인_이벤트까지_만든다() {
        // given
        UUID existing = UUID.randomUUID();
        UUID fresh = UUID.randomUUID();
        given(projectRepository.existsByPublicId(existing)).willReturn(true);
        given(projectRepository.existsByPublicId(fresh)).willReturn(false);
        given(projectRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        willAnswer(inv -> {
            inv.<Consumer<TransactionStatus>>getArgument(0).accept(null);
            return null;
        }).given(transactionTemplate).executeWithoutResult(any());

        // when
        int created = seeder().seed(List.of(project(existing, "이미 있음"), project(fresh, "새 프로젝트")));

        // then — 재배포해도 중복 생성되지 않고, 새로 만든 건만 스냅샷·색인 이벤트가 나간다
        assertThat(created).isEqualTo(1);
        ArgumentCaptor<ProjectJpaEntity> captor = ArgumentCaptor.forClass(ProjectJpaEntity.class);
        verify(projectRepository).save(captor.capture());
        assertThat(captor.getValue().getPublicId()).isEqualTo(fresh);
        assertThat(captor.getValue().getStatus()).isEqualTo("ONGOING");

        ArgumentCaptor<FundingStatusSnapshotJpaEntity> snapshot = ArgumentCaptor.forClass(FundingStatusSnapshotJpaEntity.class);
        verify(snapshotRepository).save(snapshot.capture());
        assertThat(snapshot.getValue().getParticipantCount()).isEqualTo(9);

        ArgumentCaptor<ProjectIndexedEvent> event = ArgumentCaptor.forClass(ProjectIndexedEvent.class);
        verify(indexEventPublisher).publishProjectApproved(event.capture());
        assertThat(event.getValue().publicId()).isEqualTo(fresh);
        assertThat(event.getValue().sellerDisplayName()).isEqualTo("쓱쓱생활연구소");
    }

    @Test
    void 모두_있으면_저장하지_않는다() {
        // given
        UUID existing = UUID.randomUUID();
        given(projectRepository.existsByPublicId(existing)).willReturn(true);

        // when
        int created = seeder().seed(List.of(project(existing, "이미 있음")));

        // then
        assertThat(created).isZero();
        verify(projectRepository, never()).save(any());
        verify(snapshotRepository, never()).save(any());
        verify(indexEventPublisher, never()).publishProjectApproved(any());
    }
}
