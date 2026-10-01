package com.fundit.live.infrastructure.seed;

import com.fundit.live.application.project.ProjectOwnershipClient;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 썸네일 없이 만들어진 LIVE를 프로젝트 대표 이미지로 채운다(#228). 생성 시 복사가 붙기 전에 판매자가 만든
 * LIVE가 dev에만 있어 <b>dev 전용</b>이다({@code @Profile("dev")}) — 이후 생성분은 {@code LiveCreateService}가 채운다.
 *
 * <p>비어 있는 행만 채우므로 재기동해도 결과가 같다. project-service 조회 실패는 그 프로젝트만 건너뛰고
 * 기동은 막지 않는다({@link MockLiveSeeder}와 같은 판단).
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class LiveThumbnailBackfill implements ApplicationRunner {

    private final LiveSessionJpaRepository sessionRepository;
    private final ProjectOwnershipClient projectOwnershipClient;

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("LIVE 썸네일 보완 완료 — {}건", backfill());
        } catch (RuntimeException e) {
            log.warn("LIVE 썸네일 보완 실패", e);
        }
    }

    int backfill() {
        Map<UUID, List<LiveSessionJpaEntity>> byProject = sessionRepository.findByThumbnailUrlIsNull().stream()
                .collect(Collectors.groupingBy(LiveSessionJpaEntity::getProjectId));
        int filled = 0;
        for (Map.Entry<UUID, List<LiveSessionJpaEntity>> entry : byProject.entrySet()) {
            String cover;
            try {
                cover = projectOwnershipClient.find(entry.getKey())
                        .map(ProjectOwnershipClient.ProjectOwner::coverImageUrl)
                        .orElse(null);
            } catch (RuntimeException e) {
                log.warn("LIVE 썸네일 보완 — 프로젝트 조회 실패, projectId={}", entry.getKey(), e);
                continue;
            }
            if (cover == null) {
                continue;
            }
            for (LiveSessionJpaEntity session : entry.getValue()) {
                filled += sessionRepository.fillThumbnailIfAbsent(session.getPublicId(), cover);
            }
        }
        return filled;
    }
}
