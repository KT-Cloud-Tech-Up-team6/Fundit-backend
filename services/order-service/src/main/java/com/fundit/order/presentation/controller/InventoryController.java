package com.fundit.order.presentation.controller;

import com.fundit.order.application.inventory.InventoryQueryService;
import com.fundit.order.presentation.dto.InventoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** project-service PROJECT-028이 잔여재고를 동기 조회하는 서비스 간 엔드포인트. */
@RestController
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryQueryService inventoryQueryService;

    @GetMapping("/api/v1/inventories/{rewardId}")
    public InventoryResponse getInventory(@PathVariable Long rewardId) {
        Integer remainingStock = inventoryQueryService.getRemainingStock(rewardId).orElse(null);
        return new InventoryResponse(rewardId, remainingStock);
    }
}
