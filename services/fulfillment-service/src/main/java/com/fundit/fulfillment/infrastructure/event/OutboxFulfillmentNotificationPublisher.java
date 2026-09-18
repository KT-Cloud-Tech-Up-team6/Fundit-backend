package com.fundit.fulfillment.infrastructure.event;

import com.fundit.fulfillment.application.funding.FundingParticipantsClient;
import com.fundit.fulfillment.application.funding.OrderFundingClient;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher;
import com.fundit.fulfillment.application.project.ProjectOwnershipClient;
import com.fundit.fulfillment.infrastructure.persistence.event.FulfillmentEventOutboxJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.event.FulfillmentEventOutboxJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 알림 대상 상태 변경과 같은 트랜잭션에서 아웃박스에만 적재한다. 실제 발행은
 * {@link FulfillmentEventOutboxWorker}가 재시도하며, 로깅만으로 성공 처리하지 않는다.
 *
 * <p>행 1개 = 수신자 1명 — 수신자 해석(누구에게 보낼지)은 여기서 동기 클라이언트로 확정한 뒤
 * memberId를 채워 적재한다("참여자 목록을 가진 서비스가 수신자 1명당 1건 발행", event-convention.md
 * "반드시 필요한 것 2가지" 참고). {@link ScheduleChangedEvent}는 참여자 전원이 대상이라
 * 참가자 수만큼 행을 반복 적재한다.
 */
@Component
@RequiredArgsConstructor
public class OutboxFulfillmentNotificationPublisher implements FulfillmentNotificationPublisher {

    private final FulfillmentEventOutboxJpaRepository outboxRepository;
    private final ProjectOwnershipClient projectOwnershipClient;
    private final OrderFundingClient orderFundingClient;
    private final FundingParticipantsClient fundingParticipantsClient;

    @Override
    public void publishStaleUpdateReminder(StaleUpdateReminderEvent event) {
        UUID sellerId = projectOwnershipClient.getSellerId(event.projectId());
        UUID projectPublicId = projectOwnershipClient.getPublicId(event.projectId());
        outboxRepository.save(FulfillmentEventOutboxJpaEntity.builder()
                .eventType(FulfillmentEventOutboxJpaEntity.TYPE_STALE_UPDATE_REMINDER)
                .projectPublicId(event.projectId())
                .memberId(sellerId)
                .relatedPublicId(projectPublicId)
                .build());
    }

    @Override
    public void publishScheduleChanged(ScheduleChangedEvent event) {
        UUID projectPublicId = projectOwnershipClient.getPublicId(event.projectId());
        for (UUID memberId : fundingParticipantsClient.listParticipantMemberIds(event.projectId())) {
            outboxRepository.save(FulfillmentEventOutboxJpaEntity.builder()
                    .eventType(FulfillmentEventOutboxJpaEntity.TYPE_SCHEDULE_CHANGED)
                    .projectPublicId(event.projectId())
                    .memberId(memberId)
                    .relatedPublicId(projectPublicId)
                    .stage(event.stage().name())
                    .reasonType(event.reasonType().name())
                    .newPlannedDate(event.newPlannedDate())
                    .build());
        }
    }

    @Override
    public void publishReceiptAutoConfirmed(ReceiptAutoConfirmedEvent event) {
        var snapshot = orderFundingClient.fetch(event.fundingId());
        outboxRepository.save(FulfillmentEventOutboxJpaEntity.builder()
                .eventType(FulfillmentEventOutboxJpaEntity.TYPE_RECEIPT_AUTO_CONFIRMED)
                .fundingOrderId(event.fundingId())
                .memberId(snapshot.memberId())
                .relatedPublicId(snapshot.fundingPublicId())
                .build());
    }
}
