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
