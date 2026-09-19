package com.fundit.live.infrastructure.persistence.cuesheet;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveCueSheetJpaRepository extends JpaRepository<LiveCueSheetJpaEntity, Long> {
}
