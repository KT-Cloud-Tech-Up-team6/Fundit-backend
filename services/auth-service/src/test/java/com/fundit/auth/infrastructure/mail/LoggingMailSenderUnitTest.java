package com.fundit.auth.infrastructure.mail;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingMailSenderUnitTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(LoggingMailSender.class);

    @Test
    void 모드가_없으면_스텁이_뜨지_않는다() {
        // given & when & then — 기본값으로 두면 운영에서 스텁이 조용히 선택되어
        // 재설정 메일이 로그에만 찍히고 사용자는 영영 못 받는다
        runner.run(context -> assertThat(context).doesNotHaveBean(LoggingMailSender.class));
    }

    @Test
    void 모드가_log면_뜬다() {
        // given & when & then — 로컬·개발은 application.yml의 기본값 log로 계속 동작한다
        runner.withPropertyValues("mail.mode=log")
                .run(context -> assertThat(context).hasSingleBean(LoggingMailSender.class));
    }
}
