package com.fundit.live;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.fundit")
@EnableScheduling
public class LiveServiceApplication {
    public static void main(String[] arg) {
        SpringApplication.run(LiveServiceApplication.class, arg);
    }
}
