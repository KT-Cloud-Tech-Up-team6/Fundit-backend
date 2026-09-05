package com.fundit.project.application.project;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.infrastructure.persistence.project.ProjectJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SellerServiceUnitExceptionTest {

    @Mock
    private ProjectJpaRepository projectJpaRepository;

    @InjectMocks
    private SellerService sellerService;

    @Test
    void 등록한_프로젝트가_없으면_404_예외가_발생한다() {
        // given
        UUID sellerId = UUID.randomUUID();
        when(projectJpaRepository.findBySellerIdAndDeletedAtIsNullOrderByCreatedAtDesc(sellerId)).thenReturn(List.of());

        // when & then
        assertThatThrownBy(() -> sellerService.getProfile(sellerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }
}
