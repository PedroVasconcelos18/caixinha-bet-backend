package com.caxinhabet.auth.adapter.notification;

import com.caxinhabet.auth.domain.MagicLinkSender;
import com.caxinhabet.shared.notification.ResendEmailClient;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adapter HTTP (Resend) do {@link MagicLinkSender}.
 *
 * <p>Ativo quando {@code auth.magic-link.sender=resend}. Usa a API HTTP do
 * Resend (porta 443) em vez de SMTP — PaaS como Railway bloqueiam SMTP de
 * saída. Mesmo conteúdo do {@link SmtpMagicLinkSender}; mesma disciplina de
 * falha soft (não propaga ao caller).
 */
@Component
@ConditionalOnProperty(
		prefix = "auth.magic-link",
		name = "sender",
		havingValue = "resend")
public class ResendMagicLinkSender implements MagicLinkSender {

	private static final Logger log = LoggerFactory.getLogger(ResendMagicLinkSender.class);

	private final ResendEmailClient resend;
	private final String emailFrom;

	public ResendMagicLinkSender(
			ResendEmailClient resend,
			@Value("${auth.magic-link.email-from:noreply@caixinhabet.com}") String emailFrom) {
		this.resend = resend;
		this.emailFrom = emailFrom;
	}

	@Override
	public void enviar(String email, String linkAbsoluto, Instant expiraEm) {
		try {
			resend.enviar(
					emailFrom, email, "Seu link de acesso ao Caixinha Bet",
					MagicLinkEmails.htmlAcesso(linkAbsoluto),
					MagicLinkEmails.textoAcesso(linkAbsoluto));
			log.info("Magic link Resend enviado para {}", email);
		} catch (Exception e) {
			log.error("Falha ao enviar magic link Resend para {}: {}", email, e.getMessage(), e);
		}
	}

	@Override
	public void enviarVerificacao(String email, String linkAbsoluto, Instant expiraEm) {
		try {
			resend.enviar(
					emailFrom, email, "Confirme seu e-mail no Caixinha Bet",
					MagicLinkEmails.htmlVerificacao(linkAbsoluto),
					MagicLinkEmails.textoVerificacao(linkAbsoluto));
			log.info("Verificação de e-mail Resend enviada para {}", email);
		} catch (Exception e) {
			log.error(
					"Falha ao enviar verificação de e-mail Resend para {}: {}",
					email,
					e.getMessage(),
					e);
		}
	}
}
