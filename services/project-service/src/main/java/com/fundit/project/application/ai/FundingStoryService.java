package com.fundit.project.application.ai;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.domain.aifundingstory.FundingStoryAnswer;
import com.fundit.project.domain.aifundingstory.FundingStoryResult;
import com.fundit.project.domain.aifundingstory.FundingStorySection;
import com.fundit.project.domain.project.IntroContentBlock;
import com.fundit.project.domain.project.IntroContentType;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.infrastructure.persistence.aifundingstory.AiFundingStorySessionJpaEntity;
import com.fundit.project.infrastructure.persistence.aifundingstory.AiFundingStorySessionJpaRepository;
import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 펀딩스토리 AI 정보입력/생성요청, 결과조회, 결과반영 — PROJECT-011, PROJECT-012. */
@Service
@RequiredArgsConstructor
public class FundingStoryService {

    private final ProjectRepository projectRepository;
    private final AiFundingStorySessionJpaRepository sessionJpaRepository;
    private final FundingStoryAiClient fundingStoryAiClient;

    /**
     * 실제 외부 AI 연동 전까지는 목 생성기가 동기적으로 즉시 완료 처리한다(FundingStoryAiClient
     * 클래스 주석 참고) — 그래서 이 슬라이스에서는 "생성 중 동일 세션 재요청 차단(409)"이 실제로
     * 발생할 수 없다(세션마다 새 UUID가 발급되고 생성이 즉시 끝나기 때문). 실제 비동기 연동이
     * 붙으면 그 시점에 재요청 차단 로직을 추가한다.
     */
    @Transactional
    public AiFundingStorySessionJpaEntity createSession(UUID sellerId, UUID projectPublicId,
                                                         String productDescription, List<String> productImageUrls,
                                                         List<FundingStoryAnswer> answers) {
        Project project = loadOwnedProject(sellerId, projectPublicId);

        AiFundingStorySessionJpaEntity session = sessionJpaRepository.save(AiFundingStorySessionJpaEntity.builder()
                .id(UuidCreator.getTimeOrderedEpoch())
                .projectId(project.getId())
                .sellerId(sellerId)
                .productDescription(productDescription)
                .productImageUrls(productImageUrls)
                .answers(answers)
                .build());

        FundingStoryResult result = fundingStoryAiClient.generate(productDescription, productImageUrls, answers);
        session.completeWith(result, List.of());
        return sessionJpaRepository.save(session);
    }

    @Transactional(readOnly = true)
    public AiFundingStorySessionJpaEntity getSession(UUID sellerId, UUID sessionId) {
        return loadOwnedSession(sellerId, sessionId);
    }

    @Transactional
    public Project applyToProject(UUID sellerId, UUID sessionId, String mode, Map<String, String> editsBySectionType) {
        AiFundingStorySessionJpaEntity session = loadOwnedSession(sellerId, sessionId);
        if (!AiFundingStorySessionJpaEntity.STATUS_COMPLETED.equals(session.getStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_RULE_VIOLATION, "생성이 아직 완료되지 않았습니다.");
        }

        List<IntroContentBlock> generated = toIntroContent(session.getResult(), editsBySectionType);
        Project project = projectRepository.findById(session.getProjectId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));

        List<IntroContentBlock> merged = new ArrayList<>();
        if ("COPY".equals(mode) && project.getIntroContent() != null) {
            merged.addAll(project.getIntroContent());
        }
        merged.addAll(generated);

        project.updateStory(null, null, merged);
        return projectRepository.save(project);
    }

    private List<IntroContentBlock> toIntroContent(FundingStoryResult result, Map<String, String> editsBySectionType) {
        if (result == null || result.sections() == null) return List.of();
        List<IntroContentBlock> blocks = new ArrayList<>();
        for (FundingStorySection section : result.sections()) {
            String body = editsBySectionType != null && editsBySectionType.containsKey(section.type())
                    ? editsBySectionType.get(section.type())
                    : section.body();
            blocks.add(new IntroContentBlock(IntroContentType.TEXT, body));
            if (section.images() != null) {
                for (String image : section.images()) {
                    blocks.add(new IntroContentBlock(IntroContentType.IMAGE, image));
                }
            }
        }
        return blocks;
    }

    private AiFundingStorySessionJpaEntity loadOwnedSession(UUID sellerId, UUID sessionId) {
        AiFundingStorySessionJpaEntity session = sessionJpaRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!session.getSellerId().equals(sellerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return session;
    }

    private Project loadOwnedProject(UUID sellerId, UUID projectPublicId) {
        Project project = projectRepository.findByPublicId(projectPublicId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!project.isOwnedBy(sellerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return project;
    }
}
