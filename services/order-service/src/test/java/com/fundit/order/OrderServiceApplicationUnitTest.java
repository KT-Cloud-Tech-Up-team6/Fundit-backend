package com.fundit.order;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

class OrderServiceApplicationUnitTest {

    @Test
    void 아웃박스_배치를_위해_스케줄링을_켠다() {
        assertThat(OrderServiceApplication.class.getAnnotation(EnableScheduling.class)).isNotNull();
    }

    @Test
    void 애플리케이션을_기동한다() {
        assertThat(new OrderServiceApplication()).isNotNull();

        String[] args = new String[] {};
        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            springApplication.when(() -> SpringApplication.run(OrderServiceApplication.class, args))
                    .thenReturn(null);

            OrderServiceApplication.main(args);

            springApplication.verify(() -> SpringApplication.run(OrderServiceApplication.class, args));
        }
    }
}
