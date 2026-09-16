package com.fundit.member;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.assertj.core.api.Assertions.assertThat;

class MemberServiceApplicationUnitTest {

    /**
     * 애노테이션 한 줄이라 리팩터링 중 조용히 사라지고, 사라져도 컴파일과 나머지 테스트가 전부 통과한다.
     * 그 상태로 배포되면 찜 이벤트가 영영 미발행으로 쌓인다(order-service에 같은 테스트가 있다).
     */
    @Test
    void 아웃박스_워커를_위해_스케줄링을_켠다() {
        assertThat(MemberServiceApplication.class.getAnnotation(EnableScheduling.class)).isNotNull();
    }
}
