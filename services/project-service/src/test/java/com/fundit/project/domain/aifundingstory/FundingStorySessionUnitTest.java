package com.fundit.project.domain.aifundingstory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FundingStorySessionUnitTest {

    private FundingStorySession generatingSession() {
        return FundingStorySession.create(UUID.randomUUID(), 1L, UUID.randomUUID(), "설명", null, null);
    }

    @Test
    void 생성하면_GENERATING_상태다() {
        // when
        FundingStorySession session = generatingSession();

        // then
        assertThat(session.getStatus()).isEqualTo(FundingStorySessionStatus.GENERATING);
        assertThat(session.isCompleted()).isFalse();
    }

    @Test
    void GENERATING에서_completeWith하면_COMPLETED가_된다() {
        // given
        FundingStorySession session = generatingSession();
        FundingStoryResult result = new FundingStoryResult(List.of(), List.of(), List.of());

        // when
        session.completeWith(result, List.of());

        // then
        assertThat(session.getStatus()).isEqualTo(FundingStorySessionStatus.COMPLETED);
        assertThat(session.isCompleted()).isTrue();
        assertThat(session.getResult()).isEqualTo(result);
    }

    @Test
    void 생성중_run을_폐기하면_DISCARDED가_되고_완료_callback은_결과를_확정하지_않는다() {
        // given
        FundingStorySession run = FundingStorySession.trackRun(
                UUID.randomUUID(), 1L, UUID.randomUUID(), UUID.randomUUID(), "run-key");

        // when
        boolean discarded = run.discard();
        boolean changed = run.finishRun(new FundingStoryResult("succeeded", "https://cdn/cover.png", List.of(), List.of(), null));

        // then
        assertThat(discarded).isTrue();
        assertThat(run.isDiscarded()).isTrue();
        assertThat(changed).isFalse();
        assertThat(run.getResult()).isNull();
        assertThat(run.getStatus()).isEqualTo(FundingStorySessionStatus.DISCARDED);
    }

    @Test
    void 이미_폐기된_run을_다시_폐기하면_변화가_없다() {
        // given
        FundingStorySession run = FundingStorySession.trackRun(
                UUID.randomUUID(), 1L, UUID.randomUUID(), UUID.randomUUID(), "run-key");
        run.discard();

        // when
        boolean discarded = run.discard();

        // then
        assertThat(discarded).isFalse();
        assertThat(run.isDiscarded()).isTrue();
    }

    @Test
    void 이미_완료된_run을_폐기하면_반영된_결과를_되돌리지_않는다() {
        // given
        FundingStorySession run = FundingStorySession.trackRun(
                UUID.randomUUID(), 1L, UUID.randomUUID(), UUID.randomUUID(), "run-key");
        FundingStoryResult result = new FundingStoryResult("succeeded", "https://cdn/cover.png", List.of(), List.of(), null);
        run.finishRun(result);

        // when
        boolean discarded = run.discard();

        // then
        assertThat(discarded).isFalse();
        assertThat(run.getStatus()).isEqualTo(FundingStorySessionStatus.COMPLETED);
        assertThat(run.getResult()).isEqualTo(result);
    }

    @Test
    void run_ID를_받기_전_폐기는_키를_든_DISCARDED_선점행으로_저장된다() {
        // when
        FundingStorySession preempt = FundingStorySession.preemptiveDiscard(
                UUID.randomUUID(), 1L, UUID.randomUUID(), "run-key");

        // then
        assertThat(preempt.isRunTracker()).isTrue();
        assertThat(preempt.isDiscarded()).isTrue();
        assertThat(preempt.getIdempotencyKey()).isEqualTo("run-key");
    }

    @Test
    void GENERATING에서_fail하면_FAILED가_된다() {
        // given
        FundingStorySession session = generatingSession();

        // when
        session.fail();

        // then
        assertThat(session.getStatus()).isEqualTo(FundingStorySessionStatus.FAILED);
        assertThat(session.isCompleted()).isFalse();
    }
}
