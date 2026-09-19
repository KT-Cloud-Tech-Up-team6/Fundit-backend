package com.fundit.live.domain.highlight;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LiveHighlightRepository {

    LiveHighlight save(LiveHighlight highlight);

    Optional<LiveHighlight> findByPublicId(UUID publicId);

    /** 판매자 검토용 — 비공개·실패분까지 전부. */
    List<LiveHighlight> findAllBySessionId(Long sessionId);

    /** 소비자 화면용 — 공개된 것만. */
    List<LiveHighlight> findPublicBySessionId(Long sessionId);

    long countClips(Long sessionId);

    void deleteByPublicId(UUID publicId);

    /**
     * 노출 수·클릭 수는 DB에서 한 문장으로 더한다 — 조회 후 세팅하면 동시 요청에서
     * 갱신이 덮어써진다. 그래서 도메인 필드가 아니라 포트 메서드다.
     */
    void increaseViewCount(Long sessionId);

    void increaseClickCount(UUID publicId);
}
