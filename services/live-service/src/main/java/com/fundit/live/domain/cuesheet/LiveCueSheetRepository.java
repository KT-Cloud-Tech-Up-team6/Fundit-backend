package com.fundit.live.domain.cuesheet;

import java.util.Optional;

public interface LiveCueSheetRepository {

    LiveCueSheet save(LiveCueSheet cueSheet);

    Optional<LiveCueSheet> findBySessionId(Long sessionId);
}
