package com.caxinhabet.caixinha.adapter.notification;

import com.caxinhabet.caixinha.domain.FormacaoEmail;
import com.caxinhabet.caixinha.domain.FormacaoEmailSender;
import com.caxinhabet.shared.notification.ResendEmailClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adapter HTTP (Resend) do {@link FormacaoEmailSender}.
 *
 * <p>Ativo quando {@code caixinha.formacao.sender=resend}. Mesmo conteúdo do
 * {@link SmtpFormacaoEmailSender} (celebração na formação, cuidado
 * não-acusatório na reversão); falha soft.
 */
@Component
@ConditionalOnProperty(
		prefix = "caixinha.formacao",
		name = "sender",
		havingValue = "resend")
public class ResendFormacaoEmailSender implements FormacaoEmailSender {

	private static final Logger log =
			LoggerFactory.getLogger(ResendFormacaoEmailSender.class);

	private final ResendEmailClient resend;
	private final String emailFrom;

	public ResendFormacaoEmailSender(
			ResendEmailClient resend,
			@Value("${caixinha.formacao.email-from:noreply@caixinhabet.com}")
					String emailFrom) {
		this.resend = resend;
		this.emailFrom = emailFrom;
	}

	@Override
	public void enviarAvisoFormacao(FormacaoEmail e) {
		try {
			resend.enviar(
					emailFrom,
					e.destinatario(),
					FormacaoEmails.assunto(e),
					FormacaoEmails.html(e),
					FormacaoEmails.texto(e));
			log.info(
					"Aviso de Formação ({}) Resend enviado para {} (caixinha={})",
					e.tipo(),
					e.destinatario(),
					e.tituloCaixinha());
		} catch (Exception ex) {
			log.error(
					"Falha ao enviar aviso de Formação Resend para {} (caixinha={}): {}",
					e.destinatario(),
					e.tituloCaixinha(),
					ex.getMessage(),
					ex);
		}
	}
}
