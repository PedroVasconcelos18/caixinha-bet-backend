package com.caxinhabet.pagamento.adapter.notification;

import com.caxinhabet.pagamento.domain.PremioGanhoEmail;
import com.caxinhabet.pagamento.domain.PremioGanhoEmailSender;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Adapter SMTP do {@link PremioGanhoEmailSender} (Story 4.3).
 *
 * <p>Ativo quando {@code caixinha.premio-ganho.sender=smtp}. Tom de festa
 * (NFR-6 / addendum A6). Falha de envio NÃO propaga — best-effort.
 */
@Component
@ConditionalOnProperty(
		prefix = "caixinha.premio-ganho",
		name = "sender",
		havingValue = "smtp")
public class SmtpPremioGanhoEmailSender implements PremioGanhoEmailSender {

	private static final Logger log =
			LoggerFactory.getLogger(SmtpPremioGanhoEmailSender.class);

	private final JavaMailSender mailSender;
	private final String emailFrom;

	public SmtpPremioGanhoEmailSender(
			JavaMailSender mailSender,
			@Value("${caixinha.premio-ganho.email-from:noreply@caixinhabet.com}")
					String emailFrom) {
		this.mailSender = mailSender;
		this.emailFrom = emailFrom;
	}

	@Override
	public void enviarAvisoPremio(PremioGanhoEmail e) {
		try {
			MimeMessage msg = mailSender.createMimeMessage();
			MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
			h.setFrom(emailFrom);
			h.setTo(e.destinatario());
			h.setSubject(PremioGanhoEmails.assunto(e));
			h.setText(PremioGanhoEmails.texto(e), PremioGanhoEmails.html(e));
			mailSender.send(msg);
			log.info(
					"Aviso de prêmio SMTP enviado para {} (caixinha={})",
					e.destinatario(),
					e.tituloCaixinha());
		} catch (Exception ex) {
			log.error(
					"Falha SMTP ao enviar aviso de prêmio para {} (caixinha={}): {}",
					e.destinatario(),
					e.tituloCaixinha(),
					ex.getMessage(),
					ex);
		}
	}
}
