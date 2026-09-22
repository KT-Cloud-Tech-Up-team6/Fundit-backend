package com.fundit.auth.infrastructure.mail;

import com.fundit.auth.application.mail.MailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
 * <p><b>수신자·본문은 로그에 남기지 않는다.</b> 본문에는 재설정 링크(사실상 베어러 토큰)가
 * 그대로 들어 있어, 로그로 새면 토큰을 탈취한 것과 같다(security.md S10). {@code mail.mode=log}는
 * 운영에서 실제 발송기가 붙기 전 임시값일 수 있어, "지금은 스텁이니 로그에 남겨도 된다"고
 * 가정하지 않는다.
 *
 * <p>예외는 {@code mail.log-body=true}뿐이다 — QA가 메일 없이 재설정 링크를 확인하는 용도로
 * <b>dev 프로필에서만</b> 켠다(application-dev.yml). 기본값은 false라 prod에서 {@code mail.mode=log}가
 * 쓰이더라도 본문은 남지 않는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "mail.mode", havingValue = "log", matchIfMissing = false)
public class LoggingMailSender implements MailSender {

    private final boolean logBody;

    public LoggingMailSender(@Value("${mail.log-body:false}") boolean logBody) {
        this.logBody = logBody;
    }

    @Override
    public void send(String to, String subject, String body) {
        if (logBody) {
            log.info("[stub] 메일 발송 subject={} body={}", subject, body);
            return;
        }
        log.info("[stub] 메일 발송 subject={}", subject);
    }
}
