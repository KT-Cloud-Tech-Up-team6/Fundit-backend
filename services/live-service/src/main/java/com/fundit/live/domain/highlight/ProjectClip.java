package com.fundit.live.domain.highlight;

import java.util.UUID;

/** 프로젝트 단위 공개 클립 목록의 한 건. 여러 방송의 클립이 섞이므로 어느 방송 것인지({@code liveId})를 같이 든다. */
public record ProjectClip(UUID liveId, LiveHighlight clip) {
}
