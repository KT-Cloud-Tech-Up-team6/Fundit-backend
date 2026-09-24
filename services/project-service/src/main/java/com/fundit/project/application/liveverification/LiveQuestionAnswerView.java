package com.fundit.project.application.liveverification;

/**
 * 질문(복제본)과 답변(live_verifications)을 조인한 조회 결과. 문구/건수를 답변 행에 복사하지 않고
 * 조회 시점에 합쳐 내려주기 때문에, 건수가 갱신돼도 재동기화가 필요 없다.
 *
 * <p>답변이 없으면 {@code answer}/{@code liveVerificationId}가 null이다(판매자 목록의 미답변 질문).
 */
public record LiveQuestionAnswerView(String questionSummaryId, String questionText, int questionCount,
                                     Long liveVerificationId, String answer) {

    public boolean answered() {
        return answer != null;
    }
}
