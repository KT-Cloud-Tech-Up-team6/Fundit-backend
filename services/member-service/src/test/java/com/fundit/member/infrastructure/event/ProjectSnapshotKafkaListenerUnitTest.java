package com.fundit.member.infrastructure.event;

import com.fundit.member.infrastructure.persistence.projectsnapshot.ProjectSnapshotJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProjectSnapshotKafkaListenerUnitTest {

    @Mock
    private ProjectSnapshotJpaRepository projectSnapshotJpaRepository;

    @InjectMocks
    private ProjectSnapshotKafkaListener listener;

    @Test
    void 프로젝트_승인_수정_이벤트를_스냅샷으로_반영한다() {
        // given
        UUID publicId = UUID.randomUUID();

        // when
        listener.onProjectChanged(new ProjectSnapshotKafkaListener.ProjectChangedEvent(
                10L, publicId, "에어쿡 프로", "https://img/10.png", 5L));

        // then
        verify(projectSnapshotJpaRepository).upsert(10L, publicId, "에어쿡 프로", "https://img/10.png", 5L);
    }
}
