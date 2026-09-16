package com.fundit.order.application.inventory;

import com.fundit.order.application.notification.OrderNotificationPublisher;
import com.fundit.order.application.notification.OrderNotificationPublisher.RewardRestockedEvent;
import com.fundit.order.domain.inventory.Inventory;
import com.fundit.order.domain.inventory.InventoryRepository;
import com.fundit.order.infrastructure.persistence.restock.RewardRestockNotifyRequestJpaEntity;
import com.fundit.order.infrastructure.persistence.restock.RewardRestockNotifyRequestJpaRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RewardStockSyncServiceUnitTest {

    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private RewardRestockNotifyRequestJpaRepository restockNotifyRequestJpaRepository;
    @Mock
    private OrderNotificationPublisher notificationPublisher;

    private RewardStockSyncService service;

    RewardStockSyncServiceUnitTest() {
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new RewardStockSyncService(inventoryRepository, restockNotifyRequestJpaRepository, notificationPublisher);
    }

    @Nested
    class RewardCreated_처리 {

        @Test
        void 제한재고_리워드면_재고행을_새로_만든다() {
            // given
            when(inventoryRepository.findByRewardId(1L)).thenReturn(Optional.empty());
            ArgumentCaptor<Inventory> captor = ArgumentCaptor.forClass(Inventory.class);

            // when
            service.onRewardCreated(new RewardEventListener.RewardCreatedEvent(1L, 10L, true, 100));

            // then
            verify(inventoryRepository).save(captor.capture());
            Inventory saved = captor.getValue();
            assertThat(saved.getRewardId()).isEqualTo(1L);
            assertThat(saved.getAvailableStock()).isEqualTo(100);
            assertThat(saved.getInitialQuantity()).isEqualTo(100);
            assertThat(saved.getVersion()).isEqualTo(0);
        }

        @Test
        void 무제한_리워드면_재고행을_만들지_않는다() {
            // when
            service.onRewardCreated(new RewardEventListener.RewardCreatedEvent(1L, 10L, false, null));

            // then
            verifyNoInteractions(inventoryRepository);
        }

        @Test
        void 이미_존재하는_재고에_재수신되면_델타로_멱등_반영한다() {
            // given
            Inventory existing = Inventory.builder().id(1L).rewardId(1L)
                    .availableStock(100).initialQuantity(100).version(0).build();
            when(inventoryRepository.findByRewardId(1L)).thenReturn(Optional.of(existing));
            when(inventoryRepository.applyQuantityDelta(eq(1L), anyInt(), anyInt()))
                    .thenReturn(new InventoryRepository.InventoryDeltaResult(false));

            // when
            service.onRewardCreated(new RewardEventListener.RewardCreatedEvent(1L, 10L, true, 100));

            // then
            verify(inventoryRepository).applyQuantityDelta(1L, 0, 100);
            verify(inventoryRepository, never()).save(any());
        }
    }

    @Nested
    class RewardUpdated_처리 {

        @Test
        void 수량이_증가하면_증가분만큼만_반영한다() {
            // given — 100개 중 40개 판매(availableStock=60), 150개로 증가
            Inventory existing = Inventory.builder().id(1L).rewardId(1L)
                    .availableStock(60).initialQuantity(100).version(3).build();
            when(inventoryRepository.findByRewardId(1L)).thenReturn(Optional.of(existing));
            when(inventoryRepository.applyQuantityDelta(eq(1L), anyInt(), anyInt()))
                    .thenReturn(new InventoryRepository.InventoryDeltaResult(false));

            // when
            service.onRewardUpdated(new RewardEventListener.RewardUpdatedEvent(1L, 10L, true, 150));

            // then
            verify(inventoryRepository).applyQuantityDelta(1L, 50, 150);
        }

        @Test
        void 클램프가_발생해도_예외없이_정상_처리된다() {
            // given
            Inventory existing = Inventory.builder().id(1L).rewardId(1L)
                    .availableStock(10).initialQuantity(100).version(1).build();
            when(inventoryRepository.findByRewardId(1L)).thenReturn(Optional.of(existing));
            when(inventoryRepository.applyQuantityDelta(eq(1L), anyInt(), anyInt()))
                    .thenReturn(new InventoryRepository.InventoryDeltaResult(true));

            // when / then — 예외를 던지지 않고 정상 종료(경고 로그만 남김)
            service.onRewardUpdated(new RewardEventListener.RewardUpdatedEvent(1L, 10L, true, 5));
            verify(inventoryRepository).applyQuantityDelta(1L, -95, 5);
        }

        @Test
        void 무제한으로_변경되면_기존_재고행을_삭제한다() {
            // given
            Inventory existing = Inventory.builder().id(1L).rewardId(1L)
                    .availableStock(10).initialQuantity(100).version(1).build();
            when(inventoryRepository.findByRewardId(1L)).thenReturn(Optional.of(existing));

            // when
            service.onRewardUpdated(new RewardEventListener.RewardUpdatedEvent(1L, 10L, false, null));

            // then
            verify(inventoryRepository).deleteByRewardId(1L);
        }

        @Test
        void 재고행이_없는_상태에서_무제한_수정이벤트는_아무것도_하지_않는다() {
            // given
            when(inventoryRepository.findByRewardId(1L)).thenReturn(Optional.empty());

            // when
            service.onRewardUpdated(new RewardEventListener.RewardUpdatedEvent(1L, 10L, false, null));

            // then
            verify(inventoryRepository, never()).deleteByRewardId(any());
        }

        @Test
        void 재고행이_없는_상태에서_제한_수정이벤트가_오면_새로_생성한다() {
            // given
            when(inventoryRepository.findByRewardId(1L)).thenReturn(Optional.empty());
            ArgumentCaptor<Inventory> captor = ArgumentCaptor.forClass(Inventory.class);

            // when
            service.onRewardUpdated(new RewardEventListener.RewardUpdatedEvent(1L, 10L, true, 80));

            // then
            verify(inventoryRepository, times(1)).save(captor.capture());
            assertThat(captor.getValue().getAvailableStock()).isEqualTo(80);
        }

        @Test
        void 품절이었다가_재입고되면_대기_신청자_전원에게_알림을_보낸다() {
            // given — 재고 0에서 50으로 증가(재입고)
            Inventory existing = Inventory.builder().id(1L).rewardId(1L)
                    .availableStock(0).initialQuantity(100).version(2).build();
            when(inventoryRepository.findByRewardId(1L)).thenReturn(Optional.of(existing));
            when(inventoryRepository.applyQuantityDelta(eq(1L), anyInt(), anyInt()))
                    .thenReturn(new InventoryRepository.InventoryDeltaResult(false));
            UUID memberId1 = UUID.randomUUID();
            UUID memberId2 = UUID.randomUUID();
            List<RewardRestockNotifyRequestJpaEntity> pending = List.of(
                    RewardRestockNotifyRequestJpaEntity.builder().id(1L).rewardId(1L).memberId(memberId1).build(),
                    RewardRestockNotifyRequestJpaEntity.builder().id(2L).rewardId(1L).memberId(memberId2).build());
            when(restockNotifyRequestJpaRepository.findByRewardId(1L)).thenReturn(pending);

            // when
            service.onRewardUpdated(new RewardEventListener.RewardUpdatedEvent(1L, 10L, true, 150));

            // then
            ArgumentCaptor<RewardRestockedEvent> captor = ArgumentCaptor.forClass(RewardRestockedEvent.class);
            verify(notificationPublisher, times(2)).publishRewardRestocked(captor.capture());
            assertThat(captor.getAllValues()).extracting(RewardRestockedEvent::memberId)
                    .containsExactlyInAnyOrder(memberId1, memberId2);
            verify(restockNotifyRequestJpaRepository).deleteAll(pending);
        }

        @Test
        void 재고가_남아있는_상태에서_증가하면_재입고_알림을_보내지_않는다() {
            // given — 재고 10에서 60으로 증가(품절 상태였던 적 없음)
            Inventory existing = Inventory.builder().id(1L).rewardId(1L)
                    .availableStock(10).initialQuantity(100).version(2).build();
            when(inventoryRepository.findByRewardId(1L)).thenReturn(Optional.of(existing));
            when(inventoryRepository.applyQuantityDelta(eq(1L), anyInt(), anyInt()))
                    .thenReturn(new InventoryRepository.InventoryDeltaResult(false));

            // when
            service.onRewardUpdated(new RewardEventListener.RewardUpdatedEvent(1L, 10L, true, 150));

            // then
            verifyNoInteractions(notificationPublisher);
            verify(restockNotifyRequestJpaRepository, never()).findByRewardId(any());
        }
    }
}
