package com.fundit.project.infrastructure.persistence.community;

import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CommunityAnswerJpaRepository.upsert()의 ON CONFLICT DO UPDATE가 같은 postId에
 * 두 번 호출해도 유니크 제약 위반 없이 한 행만 남기고 내용을 갱신하는지 검증한다.
 * @Modifying 커스텀 쿼리는 호출부가 트랜잭션 안에 있어야 동작하므로 테스트 클래스에
 * @Transactional을 둔다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class CommunityAnswerJpaRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private CommunityPostJpaRepository postJpaRepository;
    @Autowired
    private CommunityAnswerJpaRepository answerJpaRepository;

    @Test
    void 같은_게시글에_두번_답변하면_한_행만_남고_마지막_내용으로_갱신된다() {
        // given
        Long postId = persistPostId();
        UUID sellerId = UUID.randomUUID();

        // when
        answerJpaRepository.upsert(postId, sellerId, "첫번째 답변");
        answerJpaRepository.upsert(postId, sellerId, "수정된 답변");

        // then
        assertThat(answerJpaRepository.findByPostIdIn(List.of(postId))).hasSize(1);
        assertThat(answerJpaRepository.findByPostId(postId).orElseThrow().getContent())
                .isEqualTo("수정된 답변");
    }

    private Long persistPostId() {
        Instant now = Instant.now();
        Long projectId = projectRepository.save(Project.builder()
                .publicId(UUID.randomUUID()).sellerId(UUID.randomUUID()).status(ProjectStatus.ONGOING)
                .createdAt(now).updatedAt(now).build()).getId();
        return postJpaRepository.save(CommunityPostJpaEntity.builder()
                .projectId(projectId).memberId(UUID.randomUUID()).postType("QUESTION")
                .content("질문").build()).getId();
    }
}
