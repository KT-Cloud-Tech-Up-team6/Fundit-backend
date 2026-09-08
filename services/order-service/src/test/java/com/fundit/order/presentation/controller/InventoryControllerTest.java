package com.fundit.order.presentation.controller;

import com.fundit.order.application.inventory.InventoryQueryService;
import com.fundit.order.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryController.class)
@Import(GlobalExceptionHandler.class)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryQueryService inventoryQueryService;

    @Test
    void 재고행이_있으면_잔여수량을_반환한다() throws Exception {
        // given
        when(inventoryQueryService.getRemainingStock(1L)).thenReturn(Optional.of(37));

        // when & then
        mockMvc.perform(get("/api/v1/inventories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingStock").value(37));
    }

    @Test
    void 재고행이_없으면_remainingStock을_생략한다() throws Exception {
        // given
        when(inventoryQueryService.getRemainingStock(2L)).thenReturn(Optional.empty());

        // when & then — non_null 직렬화 설정으로 필드 자체가 생략된다
        mockMvc.perform(get("/api/v1/inventories/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingStock").doesNotExist());
    }
}
