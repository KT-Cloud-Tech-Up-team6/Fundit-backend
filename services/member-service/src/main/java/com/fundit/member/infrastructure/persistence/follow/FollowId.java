package com.fundit.member.infrastructure.persistence.follow;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/** follows의 복합 PK (member_id, seller_id). record 불가 — @IdClass는 기본 생성자를 요구한다. */
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class FollowId implements Serializable {

    private UUID memberId;
    private UUID sellerId;
}
