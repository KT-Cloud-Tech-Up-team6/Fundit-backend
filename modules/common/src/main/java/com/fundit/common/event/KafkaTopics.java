package com.fundit.common.event;

/**
 * 서비스 간 Kafka 토픽명 계약. 규약 본문은 {@code .claude/rules/event-convention.md}.
 *
 * <p>토픽명은 <b>틀려도 예외가 나지 않는다</b> — 발행자가 {@code notification.raised.v1}로 보내고
 * 구독자가 {@code notification-raised-v1}을 듣고 있으면 로그도 조용하고 알림만 영영 안 온다.
 * {@link com.fundit.common.auth.AuthHeaders}와 정확히 같은 실패 모드다(Phase 1에서
 * {@code X-Internal-Api-Key}를 양쪽이 리터럴로 들고 있다가 회원가입이 100% 실패한 전례).
 *
 * <p>modules:common에 두는 근거는 루트 {@code CLAUDE.md}의 "서비스 경계를 넘는 계약 클래스만 둔다"
 * 항목이다 — ① 프레임워크 타입 무의존 ② 로직 없음 ③ 여러 모듈이 같은 값을 공유해야 함.
 * <b>Kafka 설정 클래스는 ①에서 탈락하므로 여기 두지 않는다</b> — 설정은 각 서비스 application.yml에 둔다.
 *
 * <p>enum이 아니라 {@code static final String}인 이유: {@code @KafkaListener(topics = ...)}는
 * 컴파일 타임 상수만 받는다. enum 상수는 애노테이션 인자로 쓸 수 없다.
 *
 * <p>이벤트 payload 레코드는 공유하지 않는다 — 각 서비스가 자기 record를 선언한다.
 * 공유하면 생산자·소비자가 같이 배포돼야 하는 잠금이 생긴다. JSON이 계약이고 공유하는 건 토픽명뿐이다.
 */
public final class KafkaTopics {

    /**
     * 알림 적재 요청. 여러 서비스가 발행하고 notification-service가 구독하는 <b>단일 토픽</b>이다.
     * 도메인별로 쪼개지 않는다 — 쪼개면 notification이 각 도메인을 해석해 문구를 만들어야 하는데,
     * 그 서비스는 "무엇을 알릴지 판단하지 않는다"가 설계 전제다. 구분은 payload의 notifType이 한다.
     * 파티션 키: memberId(한 회원 기준 알림 순서 보장).
     */
    public static final String NOTIFICATION_RAISED = "notification.raised.v1";

    /** 회원 가입 완료. 발행: member / 구독: order(신규 가입 쿠폰 발급). 파티션 키: memberId. */
    public static final String MEMBER_SIGNED_UP = "member.signed-up.v1";

    /** 리워드 생성. 발행: project / 구독: order(재고 원장 동기화). 파티션 키: rewardId. */
    public static final String REWARD_CREATED = "reward.created.v1";

    /** 리워드 수량 변경. 발행: project / 구독: order(재고 원장 동기화). 파티션 키: rewardId. */
    public static final String REWARD_UPDATED = "reward.updated.v1";

    /** 펀딩 성공. 발행: order / 구독: payment(정산), fulfillment(제작·배송 착수). 파티션 키: fundingId. */
    public static final String FUNDING_SUCCEEDED = "funding.succeeded.v1";

    /** 펀딩 목표 미달. 발행: order / 구독: payment(자동 환불). 파티션 키: fundingId. */
    public static final String FUNDING_GOAL_FAILED = "funding.goal-failed.v1";

    /** 구매자 펀딩 취소. 발행: order / 구독: payment(환불). 파티션 키: fundingId. */
    public static final String FUNDING_CANCELLED_BY_MEMBER = "funding.cancelled-by-member.v1";

    /** 결제 완료. 발행: payment / 구독: order(쿠폰 사용 확정). 파티션 키: fundingId. */
    public static final String PAYMENT_COMPLETED = "payment.completed.v1";

    /** 환불 완료. 발행: payment / 구독: order(쿠폰 복원). 파티션 키: fundingId. */
    public static final String REFUND_COMPLETED = "refund.completed.v1";

    /** 프로젝트 펀딩 마감 도달. 발행: project / 구독: order(성공·실패 판정). 파티션 키: projectId. */
    public static final String PROJECT_FUNDING_DEADLINE_REACHED = "project.funding-deadline-reached.v1";

    /** 배송 완료. 발행: fulfillment / 구독: payment(최종 정산). 파티션 키: fundingId. */
    public static final String SHIPPING_COMPLETED = "shipping.completed.v1";

    /** 결제 대사 필요. 구독: payment. <b>발행처 미정</b> — 확정 시 이 주석을 채울 것. 파티션 키: fundingId. */
    public static final String PAYMENT_RECONCILIATION_REQUIRED = "payment.reconciliation-required.v1";

    private KafkaTopics() {
    }
}
