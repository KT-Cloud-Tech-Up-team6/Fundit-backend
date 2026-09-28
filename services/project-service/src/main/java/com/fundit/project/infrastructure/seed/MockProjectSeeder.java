package com.fundit.project.infrastructure.seed;

import com.fundit.project.application.project.ProjectIndexEventPublisher;
import com.fundit.project.application.project.ProjectIndexEventPublisher.ProjectIndexedEvent;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.infrastructure.persistence.fundingstatus.FundingStatusSnapshotJpaEntity;
import com.fundit.project.infrastructure.persistence.fundingstatus.FundingStatusSnapshotJpaRepository;
import com.fundit.project.infrastructure.persistence.project.ProjectJpaEntity;
import com.fundit.project.infrastructure.persistence.project.ProjectJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * dev 전용 라이브 목업 프로젝트 시더. PM이 고정한 {@code public_id}로 프로젝트 50건과 달성률 스냅샷을 넣는다 —
 * live 목업 방송이 이 값을 {@code project_id}로 들고 있어 카드에서 상세로 이어진다. 판매자 UUID는 member
 * {@code MockSellerSeeder}와 같은 값이다. 이미 있으면 건너뛰어 재배포해도 중복되지 않는다.
 *
 * <p>심사 승인과 같은 색인 이벤트도 아웃박스에 적재한다. 없으면 search(홈·검색 목록)와 member(찜 스냅샷)에
 * 목업 프로젝트가 나오지 않는다.
 */
@Slf4j
@Component
@Profile("dev")
public class MockProjectSeeder implements ApplicationRunner {

    static final String SEED_FILE = "seed/mock-projects.json";

    private final ProjectJpaRepository projectRepository;
    private final FundingStatusSnapshotJpaRepository snapshotRepository;
    private final ProjectIndexEventPublisher indexEventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public MockProjectSeeder(ProjectJpaRepository projectRepository,
                             FundingStatusSnapshotJpaRepository snapshotRepository,
                             ProjectIndexEventPublisher indexEventPublisher,
                             TransactionTemplate transactionTemplate,
                             ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.snapshotRepository = snapshotRepository;
        this.indexEventPublisher = indexEventPublisher;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int created = seed(read());
            log.info("목업 프로젝트 시드 완료 — 신규 {}건", created);
        } catch (RuntimeException | IOException e) {
            // 기동은 막지 않는다 — 다음 재시작 때 이미 있는 행은 건너뛰고 나머지만 채운다.
            log.warn("목업 프로젝트 시드 실패", e);
        }
    }

    int seed(List<MockProject> projects) {
        int created = 0;
        for (MockProject p : projects) {
            if (projectRepository.existsByPublicId(p.publicId())) {
                continue;
            }
            // 프로젝트·스냅샷·색인 이벤트를 한 트랜잭션으로 묶는다. 나뉘면 프로젝트만 저장된 행은 다음 기동 때
            // "이미 있음"으로 건너뛰어 색인 이벤트가 영영 나가지 않는다.
            transactionTemplate.executeWithoutResult(status -> save(p));
            created++;
        }
        return created;
    }

    private void save(MockProject p) {
        ProjectJpaEntity saved = projectRepository.save(ProjectJpaEntity.builder()
                .publicId(p.publicId())
                .sellerId(p.sellerId())
                .categoryMajor(p.categoryMajor())
                .categoryMinor(p.categoryMinor())
                .title(p.title())
                .goalAmount(p.goalAmount())
                .fundingStartAt(p.fundingStartAt())
                .fundingDeadline(p.fundingDeadline())
                .status(ProjectStatus.ONGOING.name())
                .build());
        MockSnapshot s = p.snapshot();
        snapshotRepository.save(FundingStatusSnapshotJpaEntity.builder()
                .projectId(saved.getId())
                .currentAmount(s.currentAmount())
                .achievementRate(s.achievementRate())
                .participantCount(s.participantCount())
                .lastSyncedAt(s.lastSyncedAt())
                .build());
        indexEventPublisher.publishProjectApproved(new ProjectIndexedEvent(
                saved.getId(), saved.getPublicId(), saved.getSellerId(), p.sellerNickname(),
                saved.getTitle(), saved.getCoverImageUrl(), saved.getCategoryMajor(), saved.getCategoryMinor(),
                saved.getGoalAmount(), saved.getFundingStartAt(), saved.getFundingDeadline(), saved.getCreatedAt()));
    }

    List<MockProject> read() throws IOException {
        try (InputStream in = new ClassPathResource(SEED_FILE).getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<>() {
            });
        }
    }

    record MockProject(UUID publicId, UUID sellerId, String sellerNickname, String categoryMajor,
                       String categoryMinor, String title, Long goalAmount, Instant fundingStartAt,
                       Instant fundingDeadline, MockSnapshot snapshot) {
    }

    record MockSnapshot(Long currentAmount, Integer achievementRate, Integer participantCount,
                        Instant lastSyncedAt) {
    }
}
