package com.fundit.live.application.session;

import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaEntity;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IVS 녹화 완료 → 다시보기 URL 저장(#222). 이벤트는 EventBridge → SQS로 들어오고
 * {@code RecordingQueuePoller}가 꺼내 여기로 넘긴다.
 *
 * <p>URL은 S3 presign이 아니라 CloudFront {@code /ivs/*} 경로다 — HLS는 하위 .ts 청크까지 서명이 필요해
 * m3u8만 presign하면 403이 난다. CDN 경로는 서명·만료가 없다.
 *
 * <p><b>대상 방송은 녹화 구간으로 고른다(#232)</b> — 채널·스트림 키가 판매자당 1개라 OBS를 켜 둔 채 방송을
 * 이어 하면 녹화 하나에 여러 방송이 담긴다. 구간은 prefix의 녹화 시작 시각(분 단위라 1분 여유)부터 이벤트
 * 수신 시각까지이고, 그 안에서 가장 먼저 시작한 방송에 붙인다. 합쳐진 녹화의 뒤 방송은 다시보기가 없다(한계).
 *
 * <p>저장하지 못하는 이벤트(녹화 시작·실패, 모르는 채널, 녹화 시각 파싱 실패, 이미 저장됨)는 예외 없이 무시한다 — 예외를 던지면
 * 메시지가 큐에 남아 재수신될 뿐 결과가 달라지지 않는다. 대신 이유를 로그에 남긴다.
 */
@Slf4j
@Service
public class LiveVodService {

    static final String RECORDING_END = "Recording End";
    /** IVS 녹화 표준 경로 — 이벤트엔 prefix만 오고 마스터 플레이리스트는 그 아래 고정 위치다(인프라 회신 10-01). */
    static final String HLS_MASTER = "media/hls/master.m3u8";
    /** IVS 녹화 prefix 끝 {@code …/yyyy/M/d/H/m/{recordingId}} — 녹화 시작 시각(UTC, 분 단위). */
    private static final Pattern RECORDING_START = Pattern.compile(
            "/(\\d{4})/(\\d{1,2})/(\\d{1,2})/(\\d{1,2})/(\\d{1,2})/[^/]+$");
    /** prefix가 분 단위로 잘려 녹화 시작이 방송 시작보다 늦게 찍힐 수 있다. */
    private static final long START_MARGIN_MINUTES = 1;

    private final LiveChannelJpaRepository channelRepository;
    private final LiveSessionJpaRepository sessionRepository;
    private final String cdnBaseUrl;

    public LiveVodService(LiveChannelJpaRepository channelRepository,
                          LiveSessionJpaRepository sessionRepository,
                          @Value("${live.vod.cdn-base-url}") String cdnBaseUrl) {
        this.channelRepository = channelRepository;
        this.sessionRepository = sessionRepository;
        this.cdnBaseUrl = trimSlashes(cdnBaseUrl);
    }

    @Transactional
    public void recordingEnded(String channelArn, String recordingStatus, String s3KeyPrefix) {
        if (!RECORDING_END.equals(recordingStatus)) {
            log.info("녹화 이벤트 무시 - 완료 이벤트 아님: status={}", safe(recordingStatus));
            return;
        }
        if (isBlank(s3KeyPrefix)) {
            log.warn("녹화 완료 무시 - 녹화 경로 없음: channelArn={}", safe(channelArn));
            return;
        }
        Optional<Instant> recordingStart = parseRecordingStart(trimSlashes(s3KeyPrefix));
        if (recordingStart.isEmpty()) {
            log.warn("녹화 완료 무시 - 녹화 시각 파싱 실패: prefix={}", safe(s3KeyPrefix));
            return;
        }
        Optional<LiveChannelJpaEntity> channel = isBlank(channelArn)
                ? Optional.empty()
                : channelRepository.findByIvsChannelArn(channelArn);
        if (channel.isEmpty()) {
            log.warn("녹화 완료 무시 - 모르는 채널: channelArn={}", safe(channelArn));
            return;
        }
        String vodUrl = cdnBaseUrl + "/" + trimSlashes(s3KeyPrefix) + "/" + HLS_MASTER;
        Instant from = recordingStart.get().minus(START_MARGIN_MINUTES, ChronoUnit.MINUTES);
        int updated = sessionRepository.fillVodIfAbsent(channel.get().getId(), vodUrl, from, Instant.now());
        if (updated == 0) {
            log.info("녹화 완료 무시 - 구간 안 방송 없음 또는 이미 저장됨: channelId={} from={}",
                    channel.get().getId(), from);
            return;
        }
        log.info("다시보기 저장: channelId={} vodUrl={}", channel.get().getId(), safe(vodUrl));
    }

    static Optional<Instant> parseRecordingStart(String prefix) {
        Matcher m = RECORDING_START.matcher(prefix);
        if (!m.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)))
                    .toInstant(ZoneOffset.UTC));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }

    private static String trimSlashes(String value) {
        return value.replaceAll("^/+|/+$", "");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 외부에서 들어온 값을 로그에 남길 때 개행·제어 문자로 로그를 위조하지 못하게 한다. */
    private static String safe(String value) {
        return value == null ? null : value.replaceAll("\\p{Cntrl}", "_");
    }
}
