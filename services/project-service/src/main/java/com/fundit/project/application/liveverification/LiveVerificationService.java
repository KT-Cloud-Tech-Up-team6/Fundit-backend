package com.fundit.project.application.liveverification;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.domain.ProjectErrorCode;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.infrastructure.persistence.liveverification.LiveQuestionSummaryJpaEntity;
import com.fundit.project.infrastructure.persistence.liveverification.LiveQuestionSummaryJpaRepository;
import com.fundit.project.infrastructure.persistence.liveverification.LiveVerificationJpaEntity;
import com.fundit.project.infrastructure.persistence.liveverification.LiveVerificationJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * LIVE검증 콘텐츠 등록/수정/삭제(판매자), 조회(공통) — PROJECT-014, PROJECT-019.
 *
 * <p>질문 문구·건수는 live-service가 발행하는 {@code live.questions-summarized.v1}만 채우고
 * ({@link #applySummaries}), 판매자는 그 질문에 답변만 붙인다. 두 경로가 같은 질문을 각자 만들지
 * 않도록 저장소를 분리했다 — 답변은 {@code live_verifications}, 질문은 {@code live_question_summaries}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveVerificationService {

    private final ProjectRepository projectRepository;
    private final LiveVerificationJpaRepository liveVerificationJpaRepository;
    private final LiveQuestionSummaryJpaRepository liveQuestionSummaryJpaRepository;

    /**
     * 수신된 질문에만 답변할 수 있다 — 이벤트로 들어온 적 없는 {@code questionSummaryId}는 404다.
     * 오타나 위조 ID로 문구 없는 항목이 생기면 소비자 탭에 "질문 없는 답변"이 그대로 노출된다.
     */
    @Transactional
    public LiveVerificationJpaEntity create(UUID sellerId, UUID projectPublicId, String questionSummaryId, String answer) {
        Project project = loadOwnedProject(sellerId, projectPublicId);
        liveQuestionSummaryJpaRepository.findByProjectIdAndQuestionSummaryId(project.getId(), questionSummaryId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.LIVE_QUESTION_SUMMARY_NOT_FOUND));
        if (liveVerificationJpaRepository.existsByProjectIdAndQuestionSummaryIdAndDeletedAtIsNull(
                project.getId(), questionSummaryId)) {
            throw new BusinessException(ProjectErrorCode.LIVE_VERIFICATION_ALREADY_EXISTS);
        }
        try {
            // IDENTITY 전략이라 save() 시점에 INSERT가 실제로 나간다 — 유니크 위반도 여기서 터진다.
            return liveVerificationJpaRepository.save(LiveVerificationJpaEntity.builder()
                    .projectId(project.getId())
                    .questionSummaryId(questionSummaryId)
                    .answer(answer)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // 위 exists 체크와 INSERT 사이에 같은 질문으로 다른 요청이 먼저 커밋된 경우
            // (uq_live_verifications_question 위반)만 409로 흡수한다 — ProjectService.create와 동일 레이스 처리.
            // 그 외 무결성 위반까지 삼키면 진짜 버그가 가짜 CONFLICT로 가려진다.
            if (!isUniqueConstraintViolation(e)) {
                throw e;
            }
            throw new BusinessException(ProjectErrorCode.LIVE_VERIFICATION_ALREADY_EXISTS);
        }
    }

    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    private boolean isUniqueConstraintViolation(DataIntegrityViolationException e) {
        Throwable cause = e.getMostSpecificCause();
        return cause instanceof SQLException sqlException
                && UNIQUE_VIOLATION_SQL_STATE.equals(sqlException.getSQLState());
    }

    @Transactional
    public LiveVerificationJpaEntity update(UUID sellerId, Long id, String answer) {
        LiveVerificationJpaEntity entity = loadOwned(sellerId, id);
        entity.changeAnswer(answer);
        return liveVerificationJpaRepository.save(entity);
    }

    @Transactional
    public void delete(UUID sellerId, Long id) {
        LiveVerificationJpaEntity entity = loadOwned(sellerId, id);
        entity.softDelete();
        liveVerificationJpaRepository.save(entity);
    }

    /** 소비자 LIVE검증 탭 — 답변이 등록된 질문만 내려간다. */
    @Transactional(readOnly = true)
    public List<LiveQuestionAnswerView> listForConsumer(UUID projectPublicId) {
        Project project = projectRepository.findByPublicId(projectPublicId)
                .filter(Project::isPublic)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        Map<String, LiveQuestionSummaryJpaEntity> summaries = summariesByQuestionId(project.getId());
        return liveVerificationJpaRepository.findByProjectIdAndDeletedAtIsNull(project.getId()).stream()
                .map(v -> toView(summaries.get(v.getQuestionSummaryId()), v))
                .toList();
    }

    /** 판매자 답변 등록 화면 — 아직 답변하지 않은 질문까지 포함해야 무엇을 올릴 수 있는지 알 수 있다. */
    @Transactional(readOnly = true)
    public List<LiveQuestionAnswerView> listQuestionsForSeller(UUID sellerId, UUID projectPublicId) {
        Project project = loadOwnedProject(sellerId, projectPublicId);
        Map<String, LiveVerificationJpaEntity> answers = liveVerificationJpaRepository
                .findByProjectIdAndDeletedAtIsNull(project.getId()).stream()
                .collect(Collectors.toMap(
                        LiveVerificationJpaEntity::getQuestionSummaryId, Function.identity(), (a, b) -> a));
        return liveQuestionSummaryJpaRepository.findByProjectIdOrderByQuestionCountDesc(project.getId()).stream()
                .map(s -> toView(s, answers.get(s.getQuestionSummaryId())))
                .toList();
    }

    /**
     * {@code live.questions-summarized.v1} 반영. Kafka는 at-least-once라 같은 이벤트가 다시 와도
     * 질문이 늘지 않도록 {@code questionSummaryId} 기준으로 upsert한다.
     *
     * <p>모르는 프로젝트(이미 삭제됐거나 다른 환경의 이벤트)는 로그만 남기고 넘어간다 — 예외를 던지면
     * 재시도 없는 에러 핸들러(KafkaConsumerConfig) 특성상 어차피 버려지고 로그만 시끄러워진다.
     */
    @Transactional
    public void applySummaries(UUID projectPublicId, List<LiveQuestionsSummarizedEvent.Summary> summaries) {
        if (projectPublicId == null || summaries == null || summaries.isEmpty()) {
            return;
        }
        Project project = projectRepository.findByPublicId(projectPublicId).orElse(null);
        if (project == null) {
            log.warn("알 수 없는 프로젝트의 LIVE 질문요약 수신, projectId={}", projectPublicId);
            return;
        }
        for (LiveQuestionsSummarizedEvent.Summary summary : summaries) {
            if (summary.questionSummaryId() == null || summary.summaryText() == null) {
                continue;
            }
            int count = summary.questionCount() == null ? 0 : summary.questionCount();
            liveQuestionSummaryJpaRepository
                    .findByProjectIdAndQuestionSummaryId(project.getId(), summary.questionSummaryId())
                    .ifPresentOrElse(
                            existing -> existing.applySummary(summary.summaryText(), count),
                            () -> liveQuestionSummaryJpaRepository.save(LiveQuestionSummaryJpaEntity.builder()
                                    .projectId(project.getId())
                                    .questionSummaryId(summary.questionSummaryId())
                                    .summaryText(summary.summaryText())
                                    .questionCount(count)
                                    .build()));
        }
    }

    private Map<String, LiveQuestionSummaryJpaEntity> summariesByQuestionId(Long projectId) {
        return liveQuestionSummaryJpaRepository.findByProjectIdOrderByQuestionCountDesc(projectId).stream()
                .collect(Collectors.toMap(
                        LiveQuestionSummaryJpaEntity::getQuestionSummaryId, Function.identity(), (a, b) -> a));
    }

    /** 컨슈머 도입 전에 등록된 답변은 대응 질문이 없다 — 문구 null, 건수 0으로 내려간다. */
    private LiveQuestionAnswerView toView(LiveQuestionSummaryJpaEntity summary, LiveVerificationJpaEntity answer) {
        String questionSummaryId = summary != null ? summary.getQuestionSummaryId() : answer.getQuestionSummaryId();
        return new LiveQuestionAnswerView(
                questionSummaryId,
                summary != null ? summary.getSummaryText() : null,
                summary != null ? summary.getQuestionCount() : 0,
                answer != null ? answer.getId() : null,
                answer != null ? answer.getAnswer() : null);
    }

    private LiveVerificationJpaEntity loadOwned(UUID sellerId, Long id) {
        LiveVerificationJpaEntity entity = liveVerificationJpaRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        Project project = projectRepository.findById(entity.getProjectId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!project.isOwnedBy(sellerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return entity;
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
