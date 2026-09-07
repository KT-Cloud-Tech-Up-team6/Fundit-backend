package com.fundit.project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ProjectServiceApplication {

    public static void main(String[] arg) {
        SpringApplication.run(ProjectServiceApplication.class, arg);
    }
}