package com.fundit.live.application.project;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * AI {@code prepare}/{@code updateContext} 배선에 필요한 프로젝트 정보 조회.
 * {@link ProjectOwnershipClient}는 소유권 검증용 최소 필드(sellerId)만 보므로 분리한다.
 */
public interface ProjectContextClient {

    Optional<ProjectContext> find(UUID projectId);

    /**
     * {@code introTexts}는 <b>평문</b>이다. project-service는 스토리 TEXT 블록을 서식이 든 HTML로
     * 내려주므로 어댑터가 태그를 걷어내고 채운다 — 그대로 AI knowledge로 넘기면 근거와 답변 본문에
     * 태그가 섞인다(#240). 다른 구현을 넣을 때도 이 계약을 지킬 것.
     */
    record ProjectContext(String title, String categoryMajor, String categoryMinor,
                          List<String> introTexts, Integer achievementRate, Integer remainingDays,
                          Instant fundingDeadline) {
    }
}
