package com.fundit.fulfillment;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

class FulfillmentServiceApplicationUnitTest {

    @Test
    void 공통_웹mvc_패키지까지_스캔한다() {
        SpringBootApplication annotation = FulfillmentServiceApplication.class.getAnnotation(SpringBootApplication.class);

        assertThat(annotation.scanBasePackages()).containsExactly("com.fundit");
    }

    @Test
    void 애플리케이션을_기동한다() {
        assertThat(new FulfillmentServiceApplication()).isNotNull();

        String[] args = new String[] {};
        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            springApplication.when(() -> SpringApplication.run(FulfillmentServiceApplication.class, args))
                    .thenReturn(null);

            FulfillmentServiceApplication.main(args);

            springApplication.verify(() -> SpringApplication.run(FulfillmentServiceApplication.class, args));
        }
    }
}
