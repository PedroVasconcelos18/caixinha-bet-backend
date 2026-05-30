package com.caxinhabet.caixinha.adapter.notification;

import com.caxinhabet.caixinha.domain.FormacaoEmail;
import com.caxinhabet.caixinha.domain.FormacaoEmailSender;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Adapter SMTP do {@link FormacaoEmailSender} (Story 3.4).
 *
 * <p>Ativo quando {@code caixinha.formacao.sender=smtp}. Conteúdo difere
 * por {@link FormacaoEmail.Tipo}: celebração na formação, cuidado
 * não-acusatório na reversão (NFR-6 / addendum A6).
 *
 * <p><b>Falha soft:</b> exceção do {@code send} NÃO propaga — a transição
 * de Formação já está no DB; aviso é best-effort.
 */
@Component
@ConditionalOnProperty(
		prefix = "caixinha.formacao",
		name = "sender",
		havingValue = "smtp")
public class SmtpFormacaoEmailSender implements FormacaoEmailSender {

	private static final Logger log =
			LoggerFactory.getLogger(SmtpFormacaoEmailSender.class);

	private final JavaMailSender mailSender;
	private final String emailFrom;

	public SmtpFormacaoEmailSender(
			JavaMailSender mailSender,
			@Value("${caixinha.formacao.email-from:noreply@caixinhabet.com}")
					String emailFrom) {
		this.mailSender = mailSender;
		this.emailFrom = emailFrom;
	}

	@Override
	public void enviarAvisoFormacao(FormacaoEmail e) {
		try {
			MimeMessage msg = mailSender.createMimeMessage();
			MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
			h.setFrom(emailFrom);
			h.setTo(e.destinatario());
			h.setSubject(FormacaoEmails.assunto(e));
			h.setText(FormacaoEmails.texto(e), FormacaoEmails.html(e));
			mailSender.send(msg);
			log.info(
					"Aviso de Formação ({}) SMTP enviado para {} (caixinha={})",
					e.tipo(),
					e.destinatario(),
					e.tituloCaixinha());
		} catch (Exception ex) {
			log.error(
					"Falha SMTP ao enviar aviso de Formação para {} (caixinha={}): {}",
					e.destinatario(),
					e.tituloCaixinha(),
					ex.getMessage(),
					ex);
		}
	}
}
