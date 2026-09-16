package com.fundit.member.infrastructure.event;

import org.springframework.stereotype.Component;

/**
 * 메시지 브로커가 아직 없어 발행을 완료할 수 없다. 로깅을 성공으로 취급하지 않고
 * 예외를 던져 워커가 아웃박스 행을 미발행 상태로 재시도하게 한다
 * (project/order/payment/fulfillment의 {@code Unconfigured*Transport}와 같은 상태).
 */
@Component
public class UnconfiguredWishEventTransport implements WishEventTransport {

    @Override
    public void sendWished(WishEvent event) {
        throw new IllegalStateException(
                "메시지 브로커가 아직 구성되지 않아 ProjectWished를 발행하지 못했습니다. eventId=" + event.eventId());
    }

    @Override
    public void sendUnwished(WishEvent event) {
        throw new IllegalStateException(
                "메시지 브로커가 아직 구성되지 않아 ProjectUnwished를 발행하지 못했습니다. eventId=" + event.eventId());
    }
}
