package com.fundit.notification.infrastructure.persistence.livenotifyrequest;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/** live_notify_requests의 복합 PK (live_id, member_id). record 불가 — @IdClass는 기본 생성자를 요구한다. */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class LiveNotifyRequestId implements Serializable {

    private UUID liveId;
    private UUID memberId;
}
