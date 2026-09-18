package com.fundit.live.application.chat;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.infrastructure.persistence.chat.ChatMessageJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * 채팅 적재(요구사항정의서 11.3.4). IVS Chat Logging → Firehose가 호출하는 내부 경로다.
 *
 * <p>리뷰 핸들러(부적절 메시지 필터링)와 분리한 이유: 리뷰 핸들러는 {@code SendMessage}마다
 * 호출되는 <b>동기 경로</b>라 거기에 DB를 끼우면 우리가 느려질 때 시청자 채팅이 같이 느려지고,
 * 우리가 죽으면 채팅이 막히거나 필터링 없이 통과한다.
 */
@Service
@RequiredArgsConstructor
public class ChatIngestService {

    private final ChatMessageJpaRepository chatMessageRepository;
    private final LiveSessionJpaRepository sessionRepository;

    /** @return 실제로 적재됐으면 true. 재전송(중복)이면 false. */
    @Transactional
    public boolean ingest(String roomArn, String ivsMessageId, UUID senderId, String content, Instant sentAt) {
        Long sessionId = sessionRepository.findByIvsChatRoomArn(roomArn)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "알 수 없는 채팅방입니다."))
                .getId();

        return chatMessageRepository.insertIgnoringConflict(
                ivsMessageId, sessionId, senderId, content, sentAt) > 0;
    }
}
