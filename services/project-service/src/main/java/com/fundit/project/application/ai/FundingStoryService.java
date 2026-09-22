package com.fundit.project.application.ai;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.application.ai.FundingStoryAiClient.ChatEventStream;
import com.fundit.project.application.ai.FundingStoryAiContracts.AsyncError;
import com.fundit.project.application.ai.FundingStoryAiContracts.ChatAcceptedResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.ConfirmRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.ConfirmResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.FailedSlot;
import com.fundit.project.application.ai.FundingStoryAiContracts.FundingStoryContext;
import com.fundit.project.application.ai.FundingStoryAiContracts.GeneratedContentBlock;
import com.fundit.project.application.ai.FundingStoryAiContracts.LatestSessionResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.MessageRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.OutputDescriptor;
import com.fundit.project.application.ai.FundingStoryAiContracts.PublicContentBlock;
import com.fundit.project.application.ai.FundingStoryAiContracts.PublicRunCreateRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.PublicRunResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.PublicStoryResult;
import com.fundit.project.application.ai.FundingStoryAiContracts.RunAcceptedResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.RunCompletionRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.RunCompletionResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.RunCreateRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.SessionResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.SuccessfulImage;
import com.fundit.project.application.ai.FundingStoryAiContracts.UploadTarget;
import com.fundit.project.application.ai.FundingStoryAiContracts.UploadTargetsRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.UploadTargetsResponse;
import com.fundit.project.application.media.MediaStorageClient;
import com.fundit.project.application.project.ProjectIndexEventPublisher;
import com.fundit.project.application.project.ProjectIndexEventPublisher.ProjectIndexedEvent;
import com.fundit.project.application.project.SellerProfileClient;
import com.fundit.project.domain.aifundingstory.FundingStoryFailedSlot;
import com.fundit.project.domain.aifundingstory.FundingStoryResult;
import com.fundit.project.domain.aifundingstory.FundingStoryRunError;
import com.fundit.project.domain.aifundingstory.FundingStorySession;
import com.fundit.project.domain.aifundingstory.FundingStorySessionRepository;
import com.fundit.project.domain.aifundingstory.FundingStorySessionStatus;
import com.fundit.project.domain.project.IntroContentBlock;
import com.fundit.project.domain.project.IntroContentType;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.reward.Reward;
import com.fundit.project.domain.reward.RewardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Funding Story 공개 프록시, Core DTO 구성, AI callback 검증과 최종 결과 반영. */
@Service
@RequiredArgsConstructor
public class FundingStoryService {

    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final String PNG = "image/png";

    private final ProjectRepository projectRepository;
    private final RewardRepository rewardRepository;
    private final FundingStorySessionRepository sessionRepository;
    private final FundingStoryAiClient fundingStoryAiClient;
    private final FundingStoryContextFactory contextFactory;
    private final MediaStorageClient storageClient;
    private final ProjectIndexEventPublisher projectIndexEventPublisher;
    private final SellerProfileClient sellerProfileClient;

    @Value("${funding-story.ai.upload-url-ttl-minutes:5}")
    private long uploadTtlMinutes;

    @Value("${funding-story.ai.run-callback-timeout-minutes:30}")
    private long runCallbackTimeoutMinutes;

    @Transactional
    public SessionResponse createSession(UUID sellerId, UUID projectPublicId) {
        Project project = loadOwnedProject(sellerId, projectPublicId);
        List<Reward> rewards = rewardRepository.findByProjectId(project.getId());
        FundingStoryContext context = contextFactory.create(project, rewards);
        SessionResponse response = fundingStoryAiClient.createSession(projectPublicId, context);
        ensureSessionTracker(response.session_id(), project, sellerId, contextFactory.fingerprint(project, rewards));
        return response;
    }

    @Transactional
    public LatestSessionResponse getLatestSession(UUID sellerId, UUID projectPublicId) {
        Project project = loadOwnedProject(sellerId, projectPublicId);
        LatestSessionResponse response = fundingStoryAiClient.getLatestSession(projectPublicId);
        if (response.session() != null) {
            List<Reward> rewards = rewardRepository.findByProjectId(project.getId());
            ensureSessionTracker(response.session().session_id(), project, sellerId,
                    contextFactory.fingerprint(project, rewards));
        }
        return response;
    }

    @Transactional(readOnly = true)
    public SessionResponse getSession(UUID sellerId, UUID projectPublicId, UUID sessionId) {
        Project project = loadOwnedProject(sellerId, projectPublicId);
        loadSessionTracker(sellerId, project, sessionId);
        return fundingStoryAiClient.getSession(projectPublicId, sessionId);
    }

    @Transactional(readOnly = true)
    public ChatAcceptedResponse startSession(UUID sellerId, UUID projectPublicId, UUID sessionId) {
        Project project = loadOwnedProject(sellerId, projectPublicId);
        loadSessionTracker(sellerId, project, sessionId);
        return fundingStoryAiClient.startSession(projectPublicId, sessionId);
    }

    @Transactional(readOnly = true)
    public ChatAcceptedResponse addMessage(
            UUID sellerId, UUID projectPublicId, UUID sessionId, MessageRequest request) {
        Project project = loadOwnedProject(sellerId, projectPublicId);
        loadSessionTracker(sellerId, project, sessionId);
        return fundingStoryAiClient.addMessage(projectPublicId, sessionId, request);
    }

    @Transactional(readOnly = true)
    public ChatEventStream openChatEvents(UUID sellerId, UUID projectPublicId, UUID chatId) {
        loadOwnedProject(sellerId, projectPublicId);
        return fundingStoryAiClient.openChatEvents(projectPublicId, chatId);
    }

    @Transactional
    public ConfirmResponse confirmSession(
            UUID sellerId, UUID projectPublicId, UUID sessionId, ConfirmRequest request) {
        Project project = loadOwnedProject(sellerId, projectPublicId);
        FundingStorySession tracker = loadSessionTracker(sellerId, project, sessionId);
        ConfirmResponse response = fundingStoryAiClient.confirmSession(projectPublicId, sessionId, request);
        tracker.confirmCoreFingerprint(contextFactory.fingerprint(
                project, rewardRepository.findByProjectId(project.getId())));
        sessionRepository.save(tracker);
        return response;
    }

    @Transactional
    public RunAcceptedResponse createRun(
            UUID sellerId, UUID projectPublicId, PublicRunCreateRequest request) {
        Project project = loadOwnedProject(sellerId, projectPublicId);
        FundingStorySession session = loadSessionTracker(sellerId, project, request.session_id());
        List<Reward> rewards = rewardRepository.findByProjectId(project.getId());
        String currentFingerprint = contextFactory.fingerprint(project, rewards);
        if (!currentFingerprint.equals(session.getCoreFingerprint())) {
            throw new BusinessException(
                    CommonErrorCode.CONFLICT,
                    "요약 확인 후 프로젝트 정보가 변경되었습니다.",
                    Map.of("action", "reconfirm_summary"));
        }

        RunAcceptedResponse response = fundingStoryAiClient.createRun(
                projectPublicId,
                new RunCreateRequest(
                        request.session_id(), request.confirmed_revision(), request.idempotency_key(),
                        contextFactory.create(project, rewards)));
        FundingStorySession existing = sessionRepository.findById(response.run_id()).orElse(null);
        if (existing == null) {
            sessionRepository.save(FundingStorySession.trackRun(
                    response.run_id(), project.getId(), sellerId, request.session_id()));
        } else if (!existing.isRunTracker()
                || !existing.isOwnedBy(sellerId)
                || !existing.getProjectId().equals(project.getId())) {
            throw new BusinessException(CommonErrorCode.CONFLICT);
        }
        return response;
    }

    @Transactional
    public PublicRunResponse getRun(UUID sellerId, UUID projectPublicId, UUID runId) {
        Project project = loadOwnedProject(sellerId, projectPublicId);
        FundingStorySession run = loadRunTracker(project, runId);
        if (!run.isOwnedBy(sellerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        if (run.getStatus() == FundingStorySessionStatus.GENERATING) {
            if (run.getCreatedAt() != null
                    && run.getCreatedAt().isBefore(Instant.now().minus(Duration.ofMinutes(runCallbackTimeoutMinutes)))) {
                FundingStoryResult timeout = new FundingStoryResult(
                        "failed", null, List.of(), List.of(),
                        new FundingStoryRunError(
                                "RUN_CALLBACK_TIMEOUT",
                                "AI 완료 결과를 제한 시간 안에 받지 못했습니다.", true, null));
                run.finishRun(timeout);
                sessionRepository.save(run);
                return publicRunResponse(runId, timeout);
            }
            return new PublicRunResponse(runId, "queued", null, List.of(), null);
        }
        FundingStoryResult result = run.getResult();
        if (result == null) {
            return new PublicRunResponse(
                    runId, "failed", null, List.of(),
                    new AsyncError("RESULT_NOT_AVAILABLE", "완료 결과를 확인할 수 없습니다.", true, null));
        }
        return publicRunResponse(runId, result);
    }

    private PublicRunResponse publicRunResponse(UUID runId, FundingStoryResult result) {
        List<FailedSlot> failedSlots = result.failedSlots().stream().map(this::toContract).toList();
        AsyncError error = result.error() == null ? null : toContract(result.error());
        PublicStoryResult publicResult = "failed".equals(result.status())
                ? null
                : new PublicStoryResult(
                        result.coverImageUrl(),
                        result.introContent().stream()
                                .map(block -> new PublicContentBlock(block.type().name(), block.value()))
                                .toList());
        return new PublicRunResponse(runId, result.status(), publicResult, failedSlots, error);
    }

    @Transactional(readOnly = true)
    public UploadTargetsResponse createUploadTargets(
            UUID projectPublicId, UploadTargetsRequest request) {
        loadProject(projectPublicId);
        if (request == null || request.outputs() == null
                || request.outputs().isEmpty() || request.outputs().size() > 30) {
            throw invalidInput();
        }
        Set<String> slots = new LinkedHashSet<>();
        for (OutputDescriptor output : request.outputs()) {
            if (output == null || output.slot_id() == null || output.slot_id().isBlank()
                    || !slots.add(output.slot_id())
                    || output.file_name() == null || !output.file_name().toLowerCase().endsWith(".png")
                    || !PNG.equals(output.content_type())
                    || output.file_size() <= 0 || output.file_size() > MAX_IMAGE_BYTES) {
                throw invalidInput();
            }
        }

        Duration ttl = Duration.ofMinutes(uploadTtlMinutes);
        Instant expiresAt = Instant.now().plus(ttl);
        List<UploadTarget> targets = request.outputs().stream().map(output -> {
            String key = "projects/%s/ai/%s.png".formatted(projectPublicId, UUID.randomUUID());
            MediaStorageClient.PresignedUpload upload = storageClient.presignPut(key, PNG, ttl);
            return new UploadTarget(output.slot_id(), upload.uploadUrl(), upload.fileUrl(), expiresAt);
        }).toList();
        return new UploadTargetsResponse(targets);
    }

    @Transactional
    public RunCompletionResponse completeRun(
            UUID projectPublicId, UUID runId, RunCompletionRequest request) {
        Project project = loadProject(projectPublicId);
        FundingStorySession run = loadRunTracker(project, runId);
        validateCompletionShape(request);

        FundingStoryResult result = "failed".equals(request.status())
                ? failedResult(request)
                : verifiedResult(projectPublicId, request);
        boolean changed = run.finishRun(result);
        if (changed) {
            if (!"failed".equals(result.status())) {
                project.updateStory(null, result.coverImageUrl(), result.introContent());
                Project saved = projectRepository.save(project);
                publishIndexUpdateIfPublic(saved);
            }
            sessionRepository.save(run);
        }
        return new RunCompletionResponse(runId, result.status());
    }

    private FundingStoryResult verifiedResult(UUID projectPublicId, RunCompletionRequest request) {
        Map<String, String> validImages = new LinkedHashMap<>();
        List<FundingStoryFailedSlot> failures = new ArrayList<>();
        request.failed_slots().forEach(slot -> failures.add(toDomain(slot)));
        String expectedPrefix = "projects/" + projectPublicId + "/ai/";

        for (SuccessfulImage image : request.successful_images()) {
            String key = storageClient.extractKey(image.file_url()).orElse(null);
            MediaStorageClient.StoredObject stored = key == null || !key.startsWith(expectedPrefix)
                    ? null
                    : storageClient.headObject(key).orElse(null);
            boolean valid = image.slot_id() != null && !image.slot_id().isBlank()
                    && PNG.equals(image.content_type())
                    && image.file_size() > 0 && image.file_size() <= MAX_IMAGE_BYTES
                    && image.width() > 0 && image.height() > 0
                    && stored != null
                    && stored.contentLength() == image.file_size()
                    && PNG.equals(stored.contentType());
            if (valid) {
                validImages.put(image.slot_id(), image.file_url());
            } else {
                failures.add(new FundingStoryFailedSlot(
                        image.slot_id(), "upload",
                        new FundingStoryRunError(
                                "STORED_OBJECT_INVALID",
                                "업로드된 이미지 검증에 실패했습니다.", true, null)));
            }
        }

        List<IntroContentBlock> intro = new ArrayList<>();
        for (GeneratedContentBlock block : request.generated_body().intro_content()) {
            if ("TEXT".equals(block.type())) {
                if (block.value() == null || block.value().isBlank() || block.slot_id() != null) {
                    throw invalidInput();
                }
                intro.add(new IntroContentBlock(IntroContentType.TEXT, block.value()));
            } else if ("IMAGE".equals(block.type())) {
                if (block.slot_id() == null || block.value() != null) {
                    throw invalidInput();
                }
                String url = validImages.get(block.slot_id());
                if (url != null) {
                    intro.add(new IntroContentBlock(IntroContentType.IMAGE, url));
                } else {
                    addReferenceFailure(failures, block.slot_id());
                }
            } else {
                throw invalidInput();
            }
        }

        String coverImageUrl = request.generated_body().cover_image_slot_id() == null
                ? null
                : validImages.get(request.generated_body().cover_image_slot_id());
        if (request.generated_body().cover_image_slot_id() != null && coverImageUrl == null) {
            addReferenceFailure(failures, request.generated_body().cover_image_slot_id());
        }

        if (coverImageUrl == null && intro.isEmpty()) {
            return new FundingStoryResult(
                    "failed", null, List.of(), failures,
                    new FundingStoryRunError(
                            "NO_USABLE_RESULT", "사용 가능한 상세페이지 결과가 없습니다.", true, null));
        }
        String status = "partially_succeeded".equals(request.status()) || !failures.isEmpty()
                ? "partially_succeeded"
                : "succeeded";
        return new FundingStoryResult(status, coverImageUrl, intro, failures, null);
    }

    private FundingStoryResult failedResult(RunCompletionRequest request) {
        return new FundingStoryResult(
                "failed", null, List.of(),
                request.failed_slots().stream().map(this::toDomain).toList(),
                toDomain(request.error()));
    }

    private void validateCompletionShape(RunCompletionRequest request) {
        if (request == null || request.status() == null
                || request.successful_images() == null || request.failed_slots() == null) {
            throw invalidInput();
        }
        Set<String> successful = new LinkedHashSet<>();
        for (SuccessfulImage image : request.successful_images()) {
            if (image == null || image.slot_id() == null || !successful.add(image.slot_id())) {
                throw invalidInput();
            }
        }
        Set<String> failed = new LinkedHashSet<>();
        for (FailedSlot slot : request.failed_slots()) {
            if (slot == null || slot.slot_id() == null || !failed.add(slot.slot_id())
                    || successful.contains(slot.slot_id()) || slot.error() == null) {
                throw invalidInput();
            }
        }
        boolean succeeded = "succeeded".equals(request.status())
                && request.generated_body() != null
                && request.generated_body().intro_content() != null
                && !request.successful_images().isEmpty()
                && request.failed_slots().isEmpty()
                && request.error() == null;
        boolean partial = "partially_succeeded".equals(request.status())
                && request.generated_body() != null
                && request.generated_body().intro_content() != null
                && !request.successful_images().isEmpty()
                && !request.failed_slots().isEmpty()
                && request.error() == null;
        boolean failedResult = "failed".equals(request.status())
                && request.generated_body() == null
                && request.successful_images().isEmpty()
                && request.error() != null;
        if (!succeeded && !partial && !failedResult) {
            throw invalidInput();
        }
    }

    private void addReferenceFailure(List<FundingStoryFailedSlot> failures, String slotId) {
        if (failures.stream().anyMatch(failure -> failure.slotId().equals(slotId))) {
            return;
        }
        failures.add(new FundingStoryFailedSlot(
                slotId, "upload",
                new FundingStoryRunError(
                        "IMAGE_REFERENCE_INVALID", "검증되지 않은 이미지 참조입니다.", true, null)));
    }

    private void ensureSessionTracker(
            UUID sessionId, Project project, UUID sellerId, String fingerprint) {
        FundingStorySession existing = sessionRepository.findById(sessionId).orElse(null);
        if (existing == null) {
            sessionRepository.save(FundingStorySession.trackSession(
                    sessionId, project.getId(), sellerId, fingerprint));
        } else if (!existing.isSessionTracker()
                || !existing.isOwnedBy(sellerId)
                || !existing.getProjectId().equals(project.getId())) {
            throw new BusinessException(CommonErrorCode.CONFLICT);
        }
    }

    private FundingStorySession loadSessionTracker(UUID sellerId, Project project, UUID sessionId) {
        FundingStorySession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!session.isOwnedBy(sellerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        if (!session.isSessionTracker() || !session.getProjectId().equals(project.getId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return session;
    }

    private FundingStorySession loadRunTracker(Project project, UUID runId) {
        FundingStorySession run = sessionRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!run.isRunTracker() || !run.getProjectId().equals(project.getId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return run;
    }

    private Project loadOwnedProject(UUID sellerId, UUID projectPublicId) {
        Project project = loadProject(projectPublicId);
        if (!project.isOwnedBy(sellerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return project;
    }

    private Project loadProject(UUID projectPublicId) {
        return projectRepository.findByPublicId(projectPublicId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private FundingStoryFailedSlot toDomain(FailedSlot slot) {
        return new FundingStoryFailedSlot(slot.slot_id(), slot.stage(), toDomain(slot.error()));
    }

    private FundingStoryRunError toDomain(AsyncError error) {
        return new FundingStoryRunError(error.code(), error.message(), error.retryable(), error.detail());
    }

    private FailedSlot toContract(FundingStoryFailedSlot slot) {
        return new FailedSlot(slot.slotId(), slot.stage(), toContract(slot.error()));
    }

    private AsyncError toContract(FundingStoryRunError error) {
        return new AsyncError(error.code(), error.message(), error.retryable(), error.detail());
    }

    private BusinessException invalidInput() {
        return new BusinessException(CommonErrorCode.INVALID_INPUT);
    }

    private void publishIndexUpdateIfPublic(Project project) {
        if (!project.isPublic()) {
            return;
        }
        String sellerDisplayName = sellerProfileClient.getDisplayName(project.getSellerId()).orElse(null);
        projectIndexEventPublisher.publishProjectUpdated(new ProjectIndexedEvent(
                project.getId(), project.getPublicId(), project.getSellerId(), sellerDisplayName,
                project.getTitle(), project.getCoverImageUrl(), project.getCategoryMajor(), project.getCategoryMinor(),
                project.getGoalAmount(), project.getFundingStartAt(), project.getFundingDeadline(),
                project.getCreatedAt()));
    }
}
