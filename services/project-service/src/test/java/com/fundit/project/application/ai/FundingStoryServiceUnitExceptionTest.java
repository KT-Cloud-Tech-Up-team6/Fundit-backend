package com.fundit.project.application.ai;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.domain.aifundingstory.FundingStorySession;
import com.fundit.project.domain.aifundingstory.FundingStorySessionRepository;
import com.fundit.project.domain.aifundingstory.FundingStorySessionStatus;
import com.fundit.project.domain.project.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundingStoryServiceUnitExceptionTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private FundingStorySessionRepository sessionRepository;
    @Mock
    private FundingStoryAiClient fundingStoryAiClient;

    @InjectMocks
    private FundingStoryService fundingStoryService;

    @Test
    void 타인_세션을_조회하면_403_예외가_발생한다() {
        // given
        UUID sessionId = UUID.randomUUID();
        FundingStorySession session = FundingStorySession.builder()
                .id(sessionId).projectId(1L).sellerId(UUID.randomUUID()).productDescription("설명")
                .status(FundingStorySessionStatus.COMPLETED).build();
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        // when & then
        assertThatThrownBy(() -> fundingStoryService.getSession(UUID.randomUUID(), sessionId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.FORBIDDEN);
    }

    @Test
    void 생성이_완료되지_않은_세션을_반영하려하면_예외가_발생한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        FundingStorySession session = FundingStorySession.builder()
                .id(sessionId).projectId(1L).sellerId(sellerId).productDescription("설명")
                .status(FundingStorySessionStatus.GENERATING).build();
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        // when & then
        assertThatThrownBy(() -> fundingStoryService.applyToProject(sellerId, sessionId, "OVERWRITE", Map.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.BUSINESS_RULE_VIOLATION);
    }
}
