package com.caxinhabet.caixinha.adapter.notification;

import com.caxinhabet.caixinha.domain.ConviteEmail;
import com.caxinhabet.caixinha.domain.ConviteEmailSender;
import com.caxinhabet.shared.notification.ResendEmailClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adapter HTTP (Resend) do {@link ConviteEmailSender}.
 *
 * <p>Ativo quando {@code caixinha.convite.sender=resend}. Usa a API HTTP do
 * Resend (443) em vez de SMTP. Mesmo conteúdo do
 * {@link SmtpConviteEmailSender}; falha soft.
 */
@Component
@ConditionalOnProperty(
		prefix = "caixinha.convite",
		name = "sender",
		havingValue = "resend")
public class ResendConviteEmailSender implements ConviteEmailSender {

	private static final Logger log = LoggerFactory.getLogger(ResendConviteEmailSender.class);

	private final ResendEmailClient resend;
	private final String emailFrom;

	public ResendConviteEmailSender(
			ResendEmailClient resend,
			@Value("${caixinha.convite.email-from:noreply@caixinhabet.com}") String emailFrom) {
		this.resend = resend;
		this.emailFrom = emailFrom;
	}

	@Override
	public void enviarConvite(ConviteEmail c) {
		try {
			resend.enviar(
					emailFrom,
					c.destinatario(),
					ConviteEmails.assunto(c),
					ConviteEmails.html(c),
					ConviteEmails.texto(c));
			log.info("Convite Resend enviado para {}", c.destinatario());
		} catch (Exception e) {
			log.error(
					"Falha ao enviar convite Resend para {} (caixinha={}): {}",
					c.destinatario(),
					c.tituloCaixinha(),
					e.getMessage(),
					e);
		}
	}
}
