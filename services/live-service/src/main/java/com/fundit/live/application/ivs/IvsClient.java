package com.fundit.live.application.ivs;

/**
 * Amazon IVS 연동 포트. 구현은 두 개다 —
 * {@code StubIvsClient}(기본)와 자격증명이 준비되면 붙일 {@code AwsIvsClient}.
 * {@code live.ivs.mode} 프로퍼티가 고른다.
 *
 * <p>이 경계를 두는 이유: 채널 프로비저닝·송출·채팅 토큰이 전부 IVS에 걸려 있어
 * 자격증명 없이는 어떤 흐름도 끝까지 확인할 수 없다. 포트를 두면 나머지 전부를
 * 지금 완성하고 IVS만 나중에 갈아끼운다.
 */
public interface IvsClient {

    /** 판매자 채널을 프로비저닝한다. 판매자당 1개라 최초 LIVE 생성 시 한 번만 호출된다. */
    Channel createChannel(String sellerId);

    /** 방송 시작 시 채팅방을 만든다. 세션당 1개. */
    String createChatRoom(String liveId);

    /**
     * IVS Chat 접속 토큰 발급. CreateChatToken은 백엔드만 호출할 수 있어서
     * <b>발급 지점이 곧 인가 지점</b>이다 — 권한 판단을 클라이언트에 맡기지 않는다.
     */
    String createChatToken(String roomArn, String userId, java.util.List<String> capabilities);

    /**
     * 현재 시청자 수. 방송 중이 아니면 0 — "실시간 순위" 정렬 대상은 이미 {@code status=LIVE}로
     * 걸러진 세션이라 여기서 예외로 전체 요청을 막을 이유가 없다(집계 실패가 목록 조회를 막으면 안 됨).
     */
    int getViewerCount(String channelArn);

    /** ARN(참조)으로 실제 스트림 키 값을 조회한다. 요청 시점에만 쓰고 저장하지 않는다(S9). */
    String getStreamKeyValue(String streamKeyRef);

    /** 서버발 이벤트. 참가자 MESSAGE가 아니라 EVENT 타입으로 도착한다 — FE가 렌더링해야 보인다. */
    void sendChatEvent(String roomArn, String eventName, java.util.Map<String, String> attributes);

    /** 채널(영구 자원) 정보. 스트림 키는 값이 아니라 비밀관리 시스템의 참조만 담는다(S9). */
    record Channel(String arn, String ingestEndpoint, String playbackUrl, String streamKeyRef) {
    }
}
