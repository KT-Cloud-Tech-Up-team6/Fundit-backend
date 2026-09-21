package com.fundit.live.application.project;

import com.fundit.live.application.ai.AiClient;

import java.util.List;
import java.util.UUID;

/** AI {@code prepare} 색인용 리워드 목록. project-service의 소비자용 리워드 조회를 그대로 옮긴다. */
public interface ProjectRewardClient {

    List<AiClient.RewardInfo> findRewards(UUID projectId);
}
