package com.fundit.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.fundit")
public class NotificationServiceApplication {

    public static void main(String[] arg) {
        SpringApplication.run(NotificationServiceApplication.class, arg);
    }
}
