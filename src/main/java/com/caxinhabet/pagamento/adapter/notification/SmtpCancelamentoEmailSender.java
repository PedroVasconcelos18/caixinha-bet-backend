package com.caxinhabet.pagamento.adapter.notification;

import com.caxinhabet.pagamento.domain.CancelamentoEmail;
import com.caxinhabet.pagamento.domain.CancelamentoEmailSender;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Adapter SMTP do {@link CancelamentoEmailSender} (Story 5.1).
 *
 * <p>Ativo quando {@code caixinha.cancelamento.sender=smtp}. Tom de
 * cuidado, não-punitivo (NFR-6) — nunca culpa ninguém. Falha de envio
 * NÃO propaga — best-effort.
 */
@Component
@ConditionalOnProperty(
		prefix = "caixinha.cancelamento",
		name = "sender",
		havingValue = "smtp")
public class SmtpCancelamentoEmailSender implements CancelamentoEmailSender {

	private static final Logger log =
			LoggerFactory.getLogger(SmtpCancelamentoEmailSender.class);

	private final JavaMailSender mailSender;
	private final String emailFrom;

	public SmtpCancelamentoEmailSender(
			JavaMailSender mailSender,
			@Value("${caixinha.cancelamento.email-from:noreply@caixinhabet.com}")
					String emailFrom) {
		this.mailSender = mailSender;
		this.emailFrom = emailFrom;
	}

	@Override
	public void enviarAvisoCancelamento(CancelamentoEmail e) {
		try {
			MimeMessage msg = mailSender.createMimeMessage();
			MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
			h.setFrom(emailFrom);
			h.setTo(e.destinatario());
			h.setSubject(CancelamentoEmails.assunto(e));
			h.setText(CancelamentoEmails.texto(e), CancelamentoEmails.html(e));
			mailSender.send(msg);
			log.info(
					"Aviso de cancelamento SMTP enviado para {} (caixinha={})",
					e.destinatario(),
					e.tituloCaixinha());
		} catch (Exception ex) {
			log.error(
					"Falha SMTP ao enviar aviso de cancelamento para {} (caixinha={}): {}",
					e.destinatario(),
					e.tituloCaixinha(),
					ex.getMessage(),
					ex);
		}
	}
}
