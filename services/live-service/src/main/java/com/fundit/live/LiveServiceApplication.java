package com.fundit.live;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EnableAsync}: 큐시트 AI 호출을 요청 스레드에서 분리한다(최대 3분+ 걸릴 수 있어
 * {@code CueSheetService}가 별도 스레드에서 부른다 — search-service의 검색 로그 적재와 같은 패턴).
 */
@SpringBootApplication(scanBasePackages = "com.fundit")
@EnableAsync
@EnableScheduling
public class LiveServiceApplication {
    public static void main(String[] arg) {
        SpringApplication.run(LiveServiceApplication.class, arg);
    }
}
