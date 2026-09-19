package com.fundit.live.application.session;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.application.ivs.IvsClient;
import com.fundit.live.application.project.ProjectOwnershipClient;
import com.fundit.live.domain.session.LiveSessionRepository;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class LiveCreateServiceUnitExceptionTest {

    @Mock private LiveSessionRepository sessionRepository;
    @Mock private LiveChannelJpaRepository channelRepository;
    @Mock private ProjectOwnershipClient projectOwnershipClient;
    @Mock private IvsClient ivsClient;

    @InjectMocks private LiveCreateService liveCreateService;

    private final UUID sellerId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @Test
    void 타인_소유_프로젝트면_403이고_아무것도_만들지_않는다() {
        // given — 식별자만으로 접근을 허용하지 않고 소유권을 검증한다(security.md S4)
        given(projectOwnershipClient.findSellerId(projectId)).willReturn(Optional.of(UUID.randomUUID()));

        // when & then
        assertThatThrownBy(() -> liveCreateService.create(sellerId, projectId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.FORBIDDEN);

        verifyNoInteractions(ivsClient, sessionRepository);
    }

    @Test
    void 없는_프로젝트면_404다() {
        // given
        given(projectOwnershipClient.findSellerId(projectId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> liveCreateService.create(sellerId, projectId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);

        verifyNoInteractions(ivsClient, sessionRepository);
    }
}
