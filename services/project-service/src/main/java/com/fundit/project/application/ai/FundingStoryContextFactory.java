package com.fundit.project.application.ai;

import com.fundit.common.error.BusinessException;
import com.fundit.project.application.ai.FundingStoryAiContracts.CategoryFact;
import com.fundit.project.application.ai.FundingStoryAiContracts.FundingStoryContext;
import com.fundit.project.application.ai.FundingStoryAiContracts.PageSummaryReward;
import com.fundit.project.application.ai.FundingStoryAiContracts.ProjectSnapshot;
import com.fundit.project.application.ai.FundingStoryAiContracts.ProjectFact;
import com.fundit.project.application.ai.FundingStoryAiContracts.RewardFact;
import com.fundit.project.application.ai.FundingStoryAiContracts.RewardOption;
import com.fundit.project.application.ai.FundingStoryAiContracts.SourceImageRef;
import com.fundit.project.application.ai.FundingStoryAiContracts.StoryContentBlock;
import com.fundit.project.application.media.MediaStorageClient;
import com.fundit.project.domain.ProjectErrorCode;
import com.fundit.project.domain.project.IntroContentBlock;
import com.fundit.project.domain.project.IntroContentType;
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

/**
 * Builds the BE-owned Core DTO and a stable fingerprint that excludes expiring signed URLs.
 * 상세 페이지 요약(#170) 입력도 같은 이미지 검사·서명 규칙으로 만든다.
 */
@Component
public class FundingStoryContextFactory {

    private static final int MAX_REWARDS = 3;
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    /** AI 입력 계약의 TEXT 블록 최대 길이. */
    private static final int MAX_TEXT_LENGTH = 20_000;

    private final MediaStorageClient storageClient;
    private final Duration readTtl;
    private final Duration pageSummaryReadTtl;

    public FundingStoryContextFactory(
            MediaStorageClient storageClient,
            @Value("${funding-story.ai.read-url-ttl-minutes:15}") long readTtlMinutes,
            @Value("${page-summary.read-url-ttl-minutes:60}") long pageSummaryReadTtlMinutes) {
        this.storageClient = storageClient;
        this.readTtl = Duration.ofMinutes(readTtlMinutes);
        this.pageSummaryReadTtl = Duration.ofMinutes(pageSummaryReadTtlMinutes);
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
        return sha256(canonical);
    }

    /** 요약 입력의 변경 감지용 해시. 서명 URL·만료 시각은 매번 달라지므로 넣지 않는다. */
    public String pageSummaryHash(Project project, List<Reward> rewards) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, project.getTitle(), category(project));
        for (Reward reward : rewards) {
            append(canonical, reward.getName(), reward.getDescription(), reward.getPrice());
        }
        for (IntroContentBlock block : storyBlocks(project)) {
            append(canonical, block.type(), block.value());
        }
        return sha256(canonical);
    }

    /**
     * AI Page Summary {@code project_snapshot}. 본문은 원래 순서대로 TEXT·IMAGE만 담는다(영상 제외).
     * IMAGE는 안정 주소를 {@code value}에, 읽기용 서명 URL은 {@code read_url}에 넣는다. GIF는 입력 계약에서
     * 빠져 건너뛰고, 그 밖에 읽을 수 없는 이미지는 요약 실패로 올린다(AI 계약: 이미지 읽기 실패는 전체 실패).
     */
    public ProjectSnapshot pageSummarySnapshot(Project project, List<Reward> rewards) {
        List<StoryContentBlock> story = new ArrayList<>();
        for (IntroContentBlock block : storyBlocks(project)) {
            if (block.type() == IntroContentType.TEXT) {
                if (block.value() != null && !block.value().isBlank()) {
                    String text = block.value().length() > MAX_TEXT_LENGTH
                            ? block.value().substring(0, MAX_TEXT_LENGTH) : block.value();
                    story.add(new StoryContentBlock("TEXT", text, null, null, null, null));
                }
                continue;
            }
            String key = keyOf(block.value());
            MediaStorageClient.StoredObject stored = headOf(key);
            if ("image/gif".equals(stored.contentType())) {
                continue;
            }
            requireImageType(stored);
            requireReadableSize(stored);
            story.add(new StoryContentBlock("IMAGE", block.value(), storageClient.presignGet(key, pageSummaryReadTtl),
                    stored.contentType(), stored.contentLength(), Instant.now().plus(pageSummaryReadTtl)));
        }
        List<PageSummaryReward> rewardFacts = rewards.stream()
                .map(reward -> new PageSummaryReward(reward.getName(), reward.getDescription(), reward.getPrice()))
                .toList();
        return new ProjectSnapshot(project.getTitle(), category(project), rewardFacts, story);
    }

    private static List<IntroContentBlock> storyBlocks(Project project) {
        return project.getIntroContent() == null ? List.of() : project.getIntroContent().stream()
                .filter(block -> block.type() == IntroContentType.TEXT || block.type() == IntroContentType.IMAGE)
                .toList();
    }

    private static String category(Project project) {
        return project.getCategoryMajor() == null ? null
                : project.getCategoryMajor() + "/" + project.getCategoryMinor();
    }

    private static String sha256(StringBuilder canonical) {
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
        String key = keyOf(fileUrl);
        MediaStorageClient.StoredObject stored = headOf(key);
        requireImageType(stored);
        requireReadableSize(stored);
        target.add(new SourceImageRef(
                slotId,
                rewardId,
                storageClient.presignGet(key, readTtl),
                stored.contentType(),
                stored.contentLength(),
                Instant.now().plus(readTtl)));
    }

    private String keyOf(String fileUrl) {
        return storageClient.extractKey(fileUrl)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.INVALID_PROJECT_DATA));
    }

    private MediaStorageClient.StoredObject headOf(String key) {
        return storageClient.headObject(key)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.INVALID_PROJECT_DATA));
    }

    private static void requireImageType(MediaStorageClient.StoredObject stored) {
        if (stored.contentType() == null || !IMAGE_TYPES.contains(stored.contentType())) {
            throw new BusinessException(ProjectErrorCode.INVALID_PROJECT_DATA);
        }
    }

    private static void requireReadableSize(MediaStorageClient.StoredObject stored) {
        if (stored.contentLength() <= 0 || stored.contentLength() > MAX_IMAGE_BYTES) {
            throw new BusinessException(ProjectErrorCode.INVALID_PROJECT_DATA);
        }
    }

    private static void append(StringBuilder target, Object... values) {
        for (Object value : values) {
            String text = value == null ? "<null>" : value.toString();
            target.append(text.length()).append(':').append(text).append('|');
        }
    }
}
