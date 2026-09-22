package com.fundit.member.application.address;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.member.infrastructure.persistence.address.AddressJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressServiceUnitExceptionTest {

    @Mock
    private AddressJpaRepository addressJpaRepository;

    @InjectMocks
    private AddressService addressService;

    @Test
    void 남의_배송지를_수정_삭제_기본지정하면_404다() {
        // given — 소유를 조회에 묶어 남의 id면 없는 것으로 본다(S4)
        UUID memberId = UUID.randomUUID();
        when(addressJpaRepository.findByIdAndMemberId(9L, memberId)).thenReturn(Optional.empty());
        AddressService.RegisterCommand command = new AddressService.RegisterCommand(
                "홍길동", "01012345678", "12345", "테헤란로 1", null, true);

        // when & then
        assertThatThrownBy(() -> addressService.update(memberId, 9L, command))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> addressService.changeDefault(memberId, 9L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> addressService.delete(memberId, 9L)).isInstanceOf(BusinessException.class);
        verify(addressJpaRepository, never()).clearDefaultExcept(any(), any());
        verify(addressJpaRepository, never()).delete(any());
    }
}
