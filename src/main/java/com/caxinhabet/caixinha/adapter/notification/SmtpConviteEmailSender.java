package com.caxinhabet.caixinha.adapter.notification;

import com.caxinhabet.caixinha.domain.ConviteEmail;
import com.caxinhabet.caixinha.domain.ConviteEmailSender;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Adapter SMTP do {@link ConviteEmailSender} (Story 2.4).
 *
 * <p>Ativo quando {@code caixinha.convite.sender=smtp}. Usa
 * {@link JavaMailSender} (configurado por {@code spring.mail.*} via env
 * vars: {@code SMTP_HOST}, {@code SMTP_PORT}, {@code SMTP_USER},
 * {@code SMTP_PASSWORD}).
 *
 * <p>Conteúdo do e-mail inline (sem template engine — `String.format`
 * basta). Tom NFR-6: linguagem de grupo de amigos, sem jargão de aposta
 * (guardrail PRD §10).
 *
 * <p><b>Falha soft:</b> se {@code JavaMailSender.send} lança, NÃO
 * propaga ao caller — só loga. O use case dispara o envio em
 * {@code afterCommit}, então uma falha aqui não reverte a criação.
 */
@Component
@ConditionalOnProperty(
		prefix = "caixinha.convite",
		name = "sender",
		havingValue = "smtp")
public class SmtpConviteEmailSender implements ConviteEmailSender {

	private static final Logger log = LoggerFactory.getLogger(SmtpConviteEmailSender.class);

	private final JavaMailSender mailSender;
	private final String emailFrom;

	public SmtpConviteEmailSender(
			JavaMailSender mailSender,
			@Value("${caixinha.convite.email-from:noreply@caixinhabet.com}") String emailFrom) {
		this.mailSender = mailSender;
		this.emailFrom = emailFrom;
	}

	@Override
	public void enviarConvite(ConviteEmail c) {
		try {
			MimeMessage msg = mailSender.createMimeMessage();
			MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
			h.setFrom(emailFrom);
			h.setTo(c.destinatario());
			h.setSubject(ConviteEmails.assunto(c));
			h.setText(ConviteEmails.texto(c), ConviteEmails.html(c));
			mailSender.send(msg);
			log.info("Convite SMTP enviado para {}", c.destinatario());
		} catch (Exception e) {
			// Falha soft: log error, não propaga. O e-mail é best-effort
			// (lição: a criação da Caixinha não pode reverter por falha SMTP).
			log.error(
					"Falha ao enviar convite SMTP para {} (caixinha={}): {}",
					c.destinatario(),
					c.tituloCaixinha(),
					e.getMessage(),
					e);
		}
	}
}
