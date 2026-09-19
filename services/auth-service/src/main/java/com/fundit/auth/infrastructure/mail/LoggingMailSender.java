package com.fundit.auth.infrastructure.mail;

import com.fundit.auth.application.mail.MailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 메일 발송 인프라가 정해지기 전까지 쓰는 스텁. {@code mail.mode=log}일 때만 뜬다.
 *
 * <p><b>{@code matchIfMissing}을 켜지 않는다.</b> 기본값으로 두면 운영에서 이 빈이 조용히
 * 선택되어 재설정 메일이 로그에만 찍히고 사용자는 영영 못 받는다. 운영 프로필은
 * {@code mail.mode: ${MAIL_MODE}}로 기본값 없이 두어 <b>미설정이면 기동이 실패</b>하게 한다
 * (live-service에서 스텁이 기본 선택되던 걸 코드리뷰에서 지적받은 그대로다).
 *
 * <p>본문에 재설정 링크가 들어가므로 {@code debug}로 남긴다 — 토큰은 운영 로그에 남기지
 * 않는다(security.md S10, auth-service CLAUDE.md).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "mail.mode", havingValue = "log", matchIfMissing = false)
public class LoggingMailSender implements MailSender {

    @Override
    public void send(String to, String subject, String body) {
        log.info("[stub] 메일 발송 to={} subject={}", to, subject);
        log.debug("[stub] 메일 본문 {}", body);
    }
}
