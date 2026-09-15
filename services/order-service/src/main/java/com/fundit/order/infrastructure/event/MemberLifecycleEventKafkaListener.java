package com.fundit.order.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.order.application.coupon.MemberLifecycleEventListener;
import com.fundit.order.application.coupon.MemberLifecycleEventListener.MemberSignedUpEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * ORDER-007 — member-service가 발행할 회원가입 이벤트를 구독해
 * {@link MemberLifecycleEventListener}(={@code WelcomeCouponAutoIssueService})로 위임하는
 * 얇은 어댑터. member-service가 아직 이 토픽으로 발행하지 않아(Tier C, `.claude/plans/` 참고)
 * 지금은 이 어댑터를 붙여도 실제 트래픽이 없다 — 발행 측이 준비되면 바로 동작한다.
 */
@Component
@RequiredArgsConstructor
public class MemberLifecycleEventKafkaListener {

    private final MemberLifecycleEventListener listener;

    @KafkaListener(topics = KafkaTopics.MEMBER_SIGNED_UP, groupId = "order-service")
    public void onMemberSignedUp(MemberSignedUpEvent event) {
        listener.onMemberSignedUp(event);
    }
}
