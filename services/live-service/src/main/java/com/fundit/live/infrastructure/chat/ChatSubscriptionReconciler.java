package com.fundit.live.infrastructure.chat;

import com.fundit.live.application.chat.ChatIngestService;
import com.fundit.live.application.ivs.IvsClient;
import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 채팅 적재(요구사항정의서 11.3.4) — 진행 중인 방송의 IVS 채팅방을 BE가 직접 구독해 받는 즉시 저장한다.
 *
 * <p>IVS Chat 로깅(Firehose·S3)을 쓰지 않는 이유: Firehose는 쓰지 못할 수 있고 S3는 약 5분 지연이라
 * AI 3분 집계({@code ChatCommentBatchSender})에 못 맞춘다. 로그 레코드엔 채팅방 ARN도 없다.
 * 구독은 인프라 구성이 필요 없고, 방별 연결이라 어느 방송의 채팅인지 바로 안다.
 *
 * <p>방송 시작·종료 코드에 끼우지 않고 이 루프 하나가 매 주기 "LIVE 세션 ↔ 연결"을 맞춘다 —
 * 시작(최대 한 주기 지연)·종료·서버 재기동·연결 끊김이 전부 같은 경로로 처리된다.
 *
 * <p>ponytail: 파드마다 구독한다(현재 live 파드 1개, 롤링 배포 중 잠깐 2개 → 메시지 id로 중복 무시).
 * replica를 늘리면 DB 락으로 한 파드만 구독하게 바꾼다.
 * <br>ponytail: 파드가 내려가 있는 사이의 메시지는 유실된다. 운영에서 문제 되면 CloudWatch 로깅으로 보강한다.
 */
@Slf4j
@Component
public class ChatSubscriptionReconciler {

    /** 구독용 토큰의 IVS userId. 보기 전용이라 이 이름으로 메시지가 나가는 일은 없다. */
    static final String INGEST_USER_ID = "live-service-ingest";
    /** IVS 채팅 세션 기본 수명이 60분이라(CreateChatToken 문서) 만료 전에 새 연결로 갈아탄다. */
    static final Duration RENEW_AFTER = Duration.ofMinutes(50);

    private final LiveSessionJpaRepository sessionRepository;
    private final IvsClient ivsClient;
    private final ChatIngestService chatIngestService;
    private final Clock clock;

    /** 세션 PK → 연결. 스케줄러 스레드에서만 만진다(fixedDelay라 주기가 겹치지 않는다). */
    private final Map<Long, Subscription> subscriptions = new HashMap<>();

    @Autowired
    public ChatSubscriptionReconciler(LiveSessionJpaRepository sessionRepository, IvsClient ivsClient,
                                      ChatIngestService chatIngestService) {
        this(sessionRepository, ivsClient, chatIngestService, Clock.systemUTC());
    }

    ChatSubscriptionReconciler(LiveSessionJpaRepository sessionRepository, IvsClient ivsClient,
                               ChatIngestService chatIngestService, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.ivsClient = ivsClient;
        this.chatIngestService = chatIngestService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${live.chat.subscription-poll-interval-ms:3000}")
    public void reconcile() {
        List<LiveSessionJpaEntity> liveSessions =
                sessionRepository.findByStatusOrderByActualStartAtDesc(LiveStatus.LIVE);
        Set<Long> liveIds = liveSessions.stream().map(LiveSessionJpaEntity::getId).collect(Collectors.toSet());
        subscriptions.entrySet().removeIf(entry -> {
            if (liveIds.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().connection().close();
            return true;
        });

        Instant now = clock.instant();
        for (LiveSessionJpaEntity session : liveSessions) {
            Subscription current = subscriptions.get(session.getId());
            if (session.getIvsChatRoomArn() == null || isHealthy(current, now)) {
                continue;
            }
            try {
                subscriptions.put(session.getId(), open(session.getIvsChatRoomArn(), now));
                // 새 연결을 먼저 연 뒤 닫는다 — 겹친 구간에 두 번 받은 메시지는 메시지 id로 무시된다.
                if (current != null) {
                    current.connection().close();
                }
            } catch (RuntimeException e) {
                log.warn("채팅 구독 실패, 다음 주기에 다시 연다. liveId={}", session.getPublicId(), e);
            }
        }
    }

    private boolean isHealthy(Subscription subscription, Instant now) {
        return subscription != null && subscription.connection().isOpen()
                && subscription.openedAt().plus(RENEW_AFTER).isAfter(now);
    }

    private Subscription open(String roomArn, Instant now) {
        String token = ivsClient.createChatToken(roomArn, INGEST_USER_ID, List.of(), Map.of());
        return new Subscription(ivsClient.openChatConnection(token, message -> ingest(roomArn, message)), now);
    }

    /** 회원이 아닌 발신자(UUID가 아닌 userId)는 적재 대상이 아니다 — 비로그인은 보기 전용이라 보낼 수도 없다. */
    void ingest(String roomArn, IvsClient.ChatMessage message) {
        UUID senderId;
        try {
            senderId = UUID.fromString(message.senderUserId());
        } catch (IllegalArgumentException e) {
            log.debug("회원이 아닌 발신자의 채팅은 적재하지 않는다. senderUserId={}", message.senderUserId());
            return;
        }
        chatIngestService.ingest(roomArn, message.id(), senderId, message.content(), message.sentAt());
    }

    record Subscription(IvsClient.ChatConnection connection, Instant openedAt) {
    }
}
