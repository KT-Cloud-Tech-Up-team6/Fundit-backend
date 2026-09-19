package com.fundit.live;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.assertj.core.api.Assertions.assertThat;

class LiveServiceApplicationUnitTest {

    /**
     * 애노테이션 한 줄이라 리팩터링 중 조용히 사라지고, 사라져도 컴파일과 나머지 테스트가 전부 통과한다.
     * 그 상태로 배포되면 아웃박스 워커가 안 돌아 live.ended.v1이 영영 미발행으로 쌓이고,
     * 방송이 끝나도 AI 질문요약·하이라이트가 하나도 만들어지지 않는다
     * (member/order-service에 같은 테스트가 있다).
     */
    @Test
    void 아웃박스_워커를_위해_스케줄링을_켠다() {
        // given
        Class<?> application = LiveServiceApplication.class;

        // when
        EnableScheduling annotation = application.getAnnotation(EnableScheduling.class);

        // then
        assertThat(annotation).isNotNull();
    }
}
