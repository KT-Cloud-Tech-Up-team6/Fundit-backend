package com.fundit.common.event;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 토픽 명명 규약({@code .claude/rules/event-convention.md} 1번)을 실행 가능한 형태로 고정한다.
 *
 * <p>규약을 문서로만 두면 나중에 {@code reward_created.v1} 같은 걸 추가해도 아무도 못 막는다.
 * 토픽명은 틀려도 예외가 안 나고 조용히 메시지만 안 가므로, 잘못된 이름은 여기서 잡아야 한다.
 */
class KafkaTopicsUnitTest {

    /** {도메인}.{사건}.v{N} — 전부 소문자, 단어 내부는 '-', 구분자는 '.'만. */
    private static final Pattern NAMING = Pattern.compile("^[a-z][a-z0-9-]*\\.[a-z][a-z0-9-]*\\.v[1-9][0-9]*$");

    private static List<String> allTopics() {
        return Arrays.stream(KafkaTopics.class.getDeclaredFields())
                .filter(f -> Modifier.isStatic(f.getModifiers()) && f.getType() == String.class)
                .map(KafkaTopicsUnitTest::valueOf)
                .toList();
    }

    private static String valueOf(Field field) {
        try {
            return (String) field.get(null);
        } catch (IllegalAccessException e) {
            throw new AssertionError("상수를 읽지 못했다: " + field.getName(), e);
        }
    }

    @Test
    void 모든_토픽명이_명명_규약을_따른다() {
        // given
        List<String> topics = allTopics();

        // when & then
        assertThat(topics).isNotEmpty();
        assertThat(topics).allSatisfy(topic ->
                assertThat(topic)
                        .as("'%s'이 {도메인}.{사건}.v{N} 형식이 아니다 — .claude/rules/event-convention.md 참고", topic)
                        .matches(NAMING));
    }

    /**
     * Kafka가 메트릭 이름에서 '.'과 '_'를 같은 것으로 취급해 충돌을 경고한다.
     * 규약이 '_'를 금지하는 이유이고, 규약 위반은 위 형식 검사에서도 걸리지만 사유가 달라 따로 둔다.
     */
    @Test
    void 토픽명에_언더스코어가_없다() {
        // given
        List<String> topics = allTopics();

        // when & then
        assertThat(topics).allSatisfy(topic -> assertThat(topic).doesNotContain("_"));
    }

    /** 상수 이름만 다르고 값이 같으면 한쪽이 의도한 토픽으로 안 간다 — 이것도 조용히 깨지는 종류다. */
    @Test
    void 중복된_토픽명이_없다() {
        // given
        List<String> topics = allTopics();

        // when & then
        assertThat(topics).doesNotHaveDuplicates();
    }
}
