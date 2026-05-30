package com.caxinhabet.caixinha.adapter.notification;

import com.caxinhabet.caixinha.domain.MinimoAtingidoEmail;
import com.caxinhabet.caixinha.domain.MinimoAtingidoEmailSender;
import com.caxinhabet.shared.notification.ResendEmailClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adapter HTTP (Resend) do {@link MinimoAtingidoEmailSender}.
 *
 * <p>Ativo quando {@code caixinha.minimo-atingido.sender=resend}. Mesmo
 * conteúdo do {@link SmtpMinimoAtingidoEmailSender}; falha soft.
 */
@Component
@ConditionalOnProperty(
		prefix = "caixinha.minimo-atingido",
		name = "sender",
		havingValue = "resend")
public class ResendMinimoAtingidoEmailSender implements MinimoAtingidoEmailSender {

	private static final Logger log =
			LoggerFactory.getLogger(ResendMinimoAtingidoEmailSender.class);

	private final ResendEmailClient resend;
	private final String emailFrom;

	public ResendMinimoAtingidoEmailSender(
			ResendEmailClient resend,
			@Value("${caixinha.minimo-atingido.email-from:noreply@caixinhabet.com}")
					String emailFrom) {
		this.resend = resend;
		this.emailFrom = emailFrom;
	}

	@Override
	public void enviarAvisoMinimoAtingido(MinimoAtingidoEmail e) {
		try {
			resend.enviar(
					emailFrom,
					e.destinatario(),
					MinimoAtingidoEmails.assunto(e),
					MinimoAtingidoEmails.html(e),
					MinimoAtingidoEmails.texto(e));
			log.info(
					"Aviso 'mínimo atingido' Resend enviado para {} (caixinha={})",
					e.destinatario(),
					e.tituloCaixinha());
		} catch (Exception ex) {
			log.error(
					"Falha ao enviar 'mínimo atingido' Resend para {} (caixinha={}): {}",
					e.destinatario(),
					e.tituloCaixinha(),
					ex.getMessage(),
					ex);
		}
	}
}
