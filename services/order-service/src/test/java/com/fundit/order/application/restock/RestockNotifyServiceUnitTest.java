package com.fundit.order.application.restock;

import com.fundit.order.infrastructure.persistence.restock.RewardRestockNotifyRequestJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestockNotifyServiceUnitTest {

    @Mock
    private RewardRestockNotifyRequestJpaRepository jpaRepository;

    @InjectMocks
    private RestockNotifyService restockNotifyService;

    @Test
    void 신규_신청이면_저장한다() {
        // given
        UUID memberId = UUID.randomUUID();
        when(jpaRepository.existsByRewardIdAndMemberId(1L, memberId)).thenReturn(false);

        // when
        restockNotifyService.request(memberId, 1L);

        // then
        verify(jpaRepository, times(1)).save(any());
    }

    @Test
    void 이미_신청했으면_다시_저장하지_않는다() {
        // given
        UUID memberId = UUID.randomUUID();
        when(jpaRepository.existsByRewardIdAndMemberId(1L, memberId)).thenReturn(true);

        // when
        restockNotifyService.request(memberId, 1L);

        // then
        verify(jpaRepository, never()).save(any());
    }

    @Test
    void 동시_중복_신청으로_유니크_제약_위반이_나도_예외를_전파하지_않는다() {
        // given
        UUID memberId = UUID.randomUUID();
        when(jpaRepository.existsByRewardIdAndMemberId(1L, memberId)).thenReturn(false);
        when(jpaRepository.save(any())).thenThrow(new DataIntegrityViolationException("중복"));

        // when & then — 예외 없이 정상 종료
        restockNotifyService.request(memberId, 1L);
    }
}
