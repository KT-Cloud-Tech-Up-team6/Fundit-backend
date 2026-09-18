package com.fundit.live.infrastructure.persistence.like;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
public class LiveLikeId implements Serializable {
    private Long sessionId;
    private UUID memberId;
}
