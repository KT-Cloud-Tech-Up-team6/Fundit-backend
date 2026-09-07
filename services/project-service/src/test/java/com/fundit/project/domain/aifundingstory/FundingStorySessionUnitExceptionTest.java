package com.fundit.project.domain.aifundingstory;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FundingStorySessionUnitExceptionTest {

    private FundingStorySession completedSession() {
        FundingStorySession session = FundingStorySession.create(
                UUID.randomUUID(), 1L, UUID.randomUUID(), "설명", null, null);
        session.completeWith(new FundingStoryResult(List.of(), List.of(), List.of()), List.of());
        return session;
    }

    @Test
    void COMPLETED_세션을_다시_완료하면_예외가_발생한다() {
        // given
        FundingStorySession session = completedSession();

        // when & then
        assertThatThrownBy(() -> session.completeWith(new FundingStoryResult(List.of(), List.of(), List.of()), List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void COMPLETED_세션을_실패_처리하면_예외가_발생한다() {
        // given
        FundingStorySession session = completedSession();

        // when & then
        assertThatThrownBy(session::fail)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.BUSINESS_RULE_VIOLATION);
    }
}
