package com.fundit.order.application.order;

import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingLineItem;
import com.fundit.order.domain.funding.FundingRepository;
import com.fundit.order.domain.funding.FundingStatus;
import com.fundit.order.domain.funding.ShippingAddress;
import com.fundit.order.domain.inventory.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentExpirationProcessorUnitTest {

    @Mock
    private FundingRepository fundingRepository;
    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private PaymentExpirationProcessor processor;

    private Funding pendingFunding() {
        return Funding.builder().id(1L).publicId(UUID.randomUUID()).memberId(UUID.randomUUID()).projectId(10L)
                .status(FundingStatus.PENDING).shippingAddress(new ShippingAddress("홍길동", "010", "12345", "주소", null))
                .shippingFee(0L).paymentExpiresAt(Instant.now().minusSeconds(1))
                .lineItems(List.of(new FundingLineItem(1L, 5L, "리워드", 2, 10_000L, List.of())))
                .createdAt(Instant.now()).build();
    }

    @Test
    void PENDING이면_만료처리하고_재고를_원복한다() {
        // given
        Funding funding = pendingFunding();
        when(fundingRepository.findById(1L)).thenReturn(Optional.of(funding));
        when(fundingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        boolean expired = processor.expireOne(1L);

        // then
        assertThat(expired).isTrue();
        assertThat(funding.getStatus()).isEqualTo(FundingStatus.PAYMENT_EXPIRED);
        verify(inventoryRepository).increaseStock(5L, 2);
    }

    @Test
    void 이미_다른_상태로_전이됐으면_아무일도_하지않는다() {
        // given
        Funding funding = pendingFunding();
        funding.cancelByMember();
        when(fundingRepository.findById(1L)).thenReturn(Optional.of(funding));

        // when
        boolean expired = processor.expireOne(1L);

        // then
        assertThat(expired).isFalse();
        verify(inventoryRepository, never()).increaseStock(any(), any(Integer.class));
        verify(fundingRepository, never()).save(any());
    }

    @Test
    void 존재하지_않으면_false를_반환한다() {
        // given
        when(fundingRepository.findById(999L)).thenReturn(Optional.empty());

        // when
        boolean expired = processor.expireOne(999L);

        // then
        assertThat(expired).isFalse();
    }
}
