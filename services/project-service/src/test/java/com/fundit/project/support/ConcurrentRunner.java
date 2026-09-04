package com.fundit.project.support;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 동시 요청 시나리오 헬퍼. 모든 스레드를 래치 앞에 세워 두었다가 한 번에 풀어,
 * 순차 실행으로 우연히 통과하는 일이 없게 한다.
 */
public final class ConcurrentRunner {

    private static final int TIMEOUT_SECONDS = 30;
    private static final int SHUTDOWN_SECONDS = 5;

    private ConcurrentRunner() {
    }

    public static Result runAll(int threadCount, Runnable task) throws InterruptedException {
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startGate.await();
                        task.run();
                        successCount.incrementAndGet();
                    } catch (Throwable e) {
                        failures.add(e);
                    } finally {
                        finishGate.countDown();
                    }
                });
            }
            startGate.countDown();
            if (!finishGate.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                executor.awaitTermination(SHUTDOWN_SECONDS, TimeUnit.SECONDS);
                throw new IllegalStateException("동시 실행이 %d초 안에 끝나지 않았습니다.".formatted(TIMEOUT_SECONDS));
            }
            return new Result(successCount.get(), List.copyOf(failures));
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(SHUTDOWN_SECONDS, TimeUnit.SECONDS);
        }
    }

    public record Result(int successCount, List<Throwable> failures) {
    }
}
