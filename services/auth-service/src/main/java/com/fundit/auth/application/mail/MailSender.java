package com.fundit.auth.application.mail;

/**
 * 메일 발송 아웃바운드 포트.
 *
 * <p>발송 인프라(SES/SMTP)가 아직 정해지지 않아 구현체는 {@code LoggingMailSender} 하나뿐이다.
 * 인프라가 붙으면 구현체만 추가하고 이 포트와 호출부는 그대로 둔다.
 */
public interface MailSender {

    void send(String to, String subject, String body);
}
