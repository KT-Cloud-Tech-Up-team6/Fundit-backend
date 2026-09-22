package com.fundit.project.application.ai;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Funding Story AI wire contract. JSON field names intentionally use snake_case. */
public final class FundingStoryAiContracts {

    private FundingStoryAiContracts() {
    }

    public record CategoryFact(String major, String minor) {
    }

    public record ProjectFact(
            String business_type, CategoryFact category, String title, Long goal_amount) {
    }

    public record RewardOption(String group_name, List<String> values) {
    }

    public record RewardFact(
            Long reward_id,
            String name,
            String description,
            Long price,
            boolean is_limited,
            Integer quantity,
            boolean is_early_bird,
            List<RewardOption> options) {
    }

    public record SourceImageRef(
            String slot_id,
            Long reward_id,
            String read_url,
            String content_type,
            long file_size,
            Instant expires_at) {
    }

    public record FundingStoryContext(
            ProjectFact project, List<RewardFact> rewards, List<SourceImageRef> source_images) {
    }

    public record SessionCreateRequest(FundingStoryContext context) {
    }

    public record PublicSessionCreateRequest() {
    }

    public record ChatMessage(String role, String text) {
    }

    public record StrengthSummary(String title, String description) {
    }

    public record StorySummary(String product, String story, List<StrengthSummary> strengths) {
    }

    public record SessionResponse(
            UUID session_id,
            int revision,
            Integer confirmed_revision,
            List<ChatMessage> messages,
            List<String> missing,
            StorySummary summary,
            UUID active_chat_id) {
    }

    public record LatestSessionResponse(SessionResponse session) {
    }

    public record ChatAcceptedResponse(UUID chat_id, String status) {
    }

    public record MessageRequest(String message_id, int revision, String text) {
    }

    public record ConfirmRequest(int revision) {
    }

    public record ConfirmResponse(UUID session_id, int confirmed_revision) {
    }

    public record PublicRunCreateRequest(
            UUID session_id, int confirmed_revision, String idempotency_key) {
    }

    public record RunCreateRequest(
            UUID session_id,
            int confirmed_revision,
            String idempotency_key,
            FundingStoryContext context) {
    }

    public record RunAcceptedResponse(UUID run_id, String status) {
    }

    public record OutputDescriptor(
            String slot_id, String file_name, String content_type, long file_size) {
    }

    public record UploadTargetsRequest(List<OutputDescriptor> outputs) {
    }

    public record UploadTarget(
            String slot_id, String upload_url, String file_url, Instant expires_at) {
    }

    public record UploadTargetsResponse(List<UploadTarget> targets) {
    }

    public record AsyncError(String code, String message, boolean retryable, Object detail) {
    }

    public record GeneratedContentBlock(String type, String value, String slot_id) {
    }

    public record GeneratedBody(
            String cover_image_slot_id, List<GeneratedContentBlock> intro_content) {
    }

    public record SuccessfulImage(
            String slot_id,
            String file_url,
            String content_type,
            long file_size,
            int width,
            int height) {
    }

    public record FailedSlot(String slot_id, String stage, AsyncError error) {
    }

    public record RunCompletionRequest(
            String status,
            GeneratedBody generated_body,
            List<SuccessfulImage> successful_images,
            List<FailedSlot> failed_slots,
            AsyncError error) {
    }

    public record RunCompletionResponse(UUID run_id, String status) {
    }

    public record PublicContentBlock(String type, String value) {
    }

    public record PublicStoryResult(
            String cover_image_url, List<PublicContentBlock> intro_content) {
    }

    public record PublicRunResponse(
            UUID run_id,
            String status,
            PublicStoryResult result,
            List<FailedSlot> failed_slots,
            AsyncError error) {
    }
}
