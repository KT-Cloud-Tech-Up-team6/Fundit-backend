package com.fundit.project.application.ai;

import com.fundit.common.error.BusinessException;
import com.fundit.project.application.ai.FundingStoryAiContracts.CategoryFact;
import com.fundit.project.application.ai.FundingStoryAiContracts.FundingStoryContext;
import com.fundit.project.application.ai.FundingStoryAiContracts.ProjectFact;
import com.fundit.project.application.ai.FundingStoryAiContracts.RewardFact;
import com.fundit.project.application.ai.FundingStoryAiContracts.RewardOption;
import com.fundit.project.application.ai.FundingStoryAiContracts.SourceImageRef;
import com.fundit.project.application.media.MediaStorageClient;
import com.fundit.project.domain.ProjectErrorCode;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.reward.Reward;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** Builds the BE-owned Core DTO and a stable fingerprint that excludes expiring signed URLs. */
@Component
public class FundingStoryContextFactory {

    private static final int MAX_REWARDS = 3;
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final MediaStorageClient storageClient;
    private final Duration readTtl;

    public FundingStoryContextFactory(
            MediaStorageClient storageClient,
            @Value("${funding-story.ai.read-url-ttl-minutes:15}") long readTtlMinutes) {
        this.storageClient = storageClient;
        this.readTtl = Duration.ofMinutes(readTtlMinutes);
    }

    public FundingStoryContext create(Project project, List<Reward> allRewards) {
        validateProject(project, allRewards);
        List<Reward> rewards = allRewards.stream().limit(MAX_REWARDS).toList();
        List<RewardFact> rewardFacts = rewards.stream().map(this::toRewardFact).toList();
        List<SourceImageRef> images = new ArrayList<>();
        addImage(images, "project.cover", null, project.getCoverImageUrl());
        for (Reward reward : rewards) {
            addImage(images, "reward." + reward.getId(), reward.getId(), reward.getImageUrl());
        }
        return new FundingStoryContext(
                new ProjectFact(
                        project.getBusinessType().name(),
                        new CategoryFact(project.getCategoryMajor(), project.getCategoryMinor()),
                        project.getTitle(),
                        project.getGoalAmount()),
                rewardFacts,
                images);
    }

    public String fingerprint(Project project, List<Reward> allRewards) {
        validateProject(project, allRewards);
        StringBuilder canonical = new StringBuilder();
        append(canonical, project.getBusinessType().name(), project.getCategoryMajor(),
                project.getCategoryMinor(), project.getTitle(), project.getGoalAmount(), project.getCoverImageUrl());
        for (Reward reward : allRewards.stream().limit(MAX_REWARDS).toList()) {
            append(canonical, reward.getId(), reward.getName(), reward.getDescription(), reward.getPrice(),
                    reward.isLimited(), reward.getQuantity(), reward.isEarlyBird(), reward.getImageUrl());
            if (reward.getOptionGroups() != null) {
                reward.getOptionGroups().forEach(group -> {
                    append(canonical, group.groupName());
                    group.values().forEach(value -> append(canonical, value));
                });
            }
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

    private void validateProject(Project project, List<Reward> rewards) {
        if (project.getBusinessType() == null
                || project.getCategoryMajor() == null
                || project.getCategoryMinor() == null
                || project.getTitle() == null
                || project.getGoalAmount() == null
                || rewards == null
                || rewards.isEmpty()) {
            throw new BusinessException(ProjectErrorCode.INVALID_PROJECT_DATA);
        }
    }

    private RewardFact toRewardFact(Reward reward) {
        if (reward.getId() == null || reward.getName() == null || reward.getDescription() == null
                || reward.getPrice() == null) {
            throw new BusinessException(ProjectErrorCode.INVALID_PROJECT_DATA);
        }
        List<RewardOption> options = reward.getOptionGroups() == null
                ? List.of()
                : reward.getOptionGroups().stream()
                        .map(group -> new RewardOption(group.groupName(), group.values()))
                        .toList();
        return new RewardFact(
                reward.getId(), reward.getName(), reward.getDescription(), reward.getPrice(),
                reward.isLimited(), reward.getQuantity(), reward.isEarlyBird(), options);
    }

    private void addImage(List<SourceImageRef> target, String slotId, Long rewardId, String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }
        String key = storageClient.extractKey(fileUrl)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.INVALID_PROJECT_DATA));
        MediaStorageClient.StoredObject stored = storageClient.headObject(key)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.INVALID_PROJECT_DATA));
        if (stored.contentLength() <= 0 || stored.contentLength() > MAX_IMAGE_BYTES
                || !IMAGE_TYPES.contains(stored.contentType())) {
            throw new BusinessException(ProjectErrorCode.INVALID_PROJECT_DATA);
        }
        target.add(new SourceImageRef(
                slotId,
                rewardId,
                storageClient.presignGet(key, readTtl),
                stored.contentType(),
                stored.contentLength(),
                Instant.now().plus(readTtl)));
    }

    private static void append(StringBuilder target, Object... values) {
        for (Object value : values) {
            String text = value == null ? "<null>" : value.toString();
            target.append(text.length()).append(':').append(text).append('|');
        }
    }
}
