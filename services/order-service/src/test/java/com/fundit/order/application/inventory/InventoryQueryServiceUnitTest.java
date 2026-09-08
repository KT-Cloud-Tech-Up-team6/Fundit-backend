package com.fundit.order.application.inventory;

import com.fundit.order.domain.inventory.Inventory;
import com.fundit.order.domain.inventory.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryQueryServiceUnitTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryQueryService inventoryQueryService;

    @Test
    void 재고행이_있으면_잔여수량을_반환한다() {
        // given
        Inventory inventory = Inventory.builder().id(1L).rewardId(1L)
                .availableStock(37).initialQuantity(100).version(2).build();
        when(inventoryRepository.findByRewardId(1L)).thenReturn(Optional.of(inventory));

        // when
        Optional<Integer> result = inventoryQueryService.getRemainingStock(1L);

        // then
        assertThat(result).contains(37);
    }

    @Test
    void 재고행이_없으면_빈값을_반환한다() {
        // given
        when(inventoryRepository.findByRewardId(2L)).thenReturn(Optional.empty());

        // when
        Optional<Integer> result = inventoryQueryService.getRemainingStock(2L);

        // then
        assertThat(result).isEmpty();
    }
}
