package com.fundit.order.application.order;

import com.fundit.order.application.funding.FundingEventPublisher;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCancelServiceUnitTest {

    @Mock
    private FundingRepository fundingRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private FundingEventPublisher fundingEventPublisher;

    @InjectMocks
    private OrderCancelService orderCancelService;

    private Funding pendingFunding(UUID memberId, UUID publicId) {
        return Funding.builder().id(1L).publicId(publicId).memberId(memberId).projectId(10L).projectTitle("프로젝트")
                .status(FundingStatus.PENDING)
                .shippingAddress(new ShippingAddress("홍길동", "010", "12345", "주소", null))
                .shippingFee(3_000L).paymentExpiresAt(Instant.now().plusSeconds(1800))
                .lineItems(List.of(new FundingLineItem(1L, 5L, "리워드", 3, 10_000L, List.of())))
                .createdAt(Instant.now()).build();
    }

    @Test
    void 취소하면_재고를_원복하고_이벤트를_발행한다() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Funding funding = pendingFunding(memberId, orderId);
        when(fundingRepository.findByPublicId(orderId)).thenReturn(Optional.of(funding));
        when(fundingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        Funding result = orderCancelService.cancel(memberId, orderId);

        // then
        assertThat(result.getStatus()).isEqualTo(FundingStatus.CANCELLED_BY_MEMBER);
        verify(inventoryRepository).increaseStock(5L, 3);
        verify(fundingEventPublisher).publishFundingCancelledByMember(
                new FundingEventPublisher.FundingCancelledByMemberEvent(1L, 10L, memberId));
    }
}
