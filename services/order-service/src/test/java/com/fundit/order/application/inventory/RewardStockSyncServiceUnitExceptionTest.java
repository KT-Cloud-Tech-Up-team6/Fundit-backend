package com.fundit.order.application.inventory;

import com.fundit.order.domain.inventory.Inventory;
import com.fundit.order.domain.inventory.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RewardStockSyncServiceUnitExceptionTest {

    @Mock
    private InventoryRepository inventoryRepository;

    private RewardStockSyncService service() {
        return new RewardStockSyncService(inventoryRepository);
    }

    @Test
    void 생성이벤트가_제한재고인데_수량이_없으면_예외가_발생한다() {
        assertThatThrownBy(() -> service().onRewardCreated(
                new RewardEventListener.RewardCreatedEvent(1L, 10L, true, null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 수정이벤트가_제한재고인데_수량이_없고_재고행도_없으면_예외가_발생한다() {
        lenient().when(inventoryRepository.findByRewardId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().onRewardUpdated(
                new RewardEventListener.RewardUpdatedEvent(1L, 10L, true, null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 수정이벤트가_제한재고인데_수량이_없고_재고행은_있으면_예외가_발생한다() {
        Inventory existing = Inventory.builder().id(1L).rewardId(1L)
                .availableStock(10).initialQuantity(100).version(1).build();
        lenient().when(inventoryRepository.findByRewardId(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service().onRewardUpdated(
                new RewardEventListener.RewardUpdatedEvent(1L, 10L, true, null)))
                .isInstanceOf(IllegalStateException.class);
    }
}
