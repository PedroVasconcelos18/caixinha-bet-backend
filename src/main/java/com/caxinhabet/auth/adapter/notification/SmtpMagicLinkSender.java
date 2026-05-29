package com.caxinhabet.auth.adapter.notification;

import com.caxinhabet.auth.domain.MagicLinkSender;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Adapter SMTP do {@link MagicLinkSender} (Story 2.4 — destravado o
 * TODO da Story 2.1).
 *
 * <p>Ativo quando {@code auth.magic-link.sender=smtp}. Configurado por
 * {@code spring.mail.*} (env vars: {@code SMTP_*}).
 *
 * <p>Falha soft (não propaga ao caller). O envio fica fora da transação
 * do {@code SolicitarAcessoUseCase} — mesma estratégia da Story 2.4 para
 * convites: melhor ter solicitação persistida sem e-mail entregue do que
 * reverter a transação por SMTP indisponível.
 */
@Component
@ConditionalOnProperty(
		prefix = "auth.magic-link",
		name = "sender",
		havingValue = "smtp")
public class SmtpMagicLinkSender implements MagicLinkSender {

	private static final Logger log = LoggerFactory.getLogger(SmtpMagicLinkSender.class);

	private final JavaMailSender mailSender;
	private final String emailFrom;

	public SmtpMagicLinkSender(
			JavaMailSender mailSender,
			@Value("${auth.magic-link.email-from:noreply@caixinhabet.com}") String emailFrom) {
		this.mailSender = mailSender;
		this.emailFrom = emailFrom;
	}

	@Override
	public void enviar(String email, String linkAbsoluto, Instant expiraEm) {
		try {
			SimpleMailMessage msg = new SimpleMailMessage();
			msg.setFrom(emailFrom);
			msg.setTo(email);
			msg.setSubject("Seu link de acesso ao Caixinha Bet");
			msg.setText(
					"Oi!\n\n"
							+ "Clica no link abaixo para entrar no Caixinha Bet. Ele vale por"
							+ " 15 minutos e só pode ser usado uma vez:\n\n"
							+ linkAbsoluto
							+ "\n\n"
							+ "Se não foi você que pediu, é só ignorar este e-mail — sem"
							+ " ações na sua conta.\n\n"
							+ "Até já,\n"
							+ "Caixinha Bet");
			mailSender.send(msg);
			log.info("Magic link SMTP enviado para {}", email);
		} catch (Exception e) {
			log.error("Falha ao enviar magic link SMTP para {}: {}", email, e.getMessage(), e);
		}
	}

	@Override
	public void enviarVerificacao(String email, String linkAbsoluto, Instant expiraEm) {
		try {
			SimpleMailMessage msg = new SimpleMailMessage();
			msg.setFrom(emailFrom);
			msg.setTo(email);
			msg.setSubject("Confirme seu e-mail no Caixinha Bet");
			msg.setText(
					"Oi!\n\n"
							+ "Para concluir seu cadastro no Caixinha Bet, confirme seu"
							+ " e-mail clicando no link abaixo. Ele vale por 24 horas e só"
							+ " pode ser usado uma vez:\n\n"
							+ linkAbsoluto
							+ "\n\n"
							+ "Se não foi você que pediu, é só ignorar este e-mail — sem"
							+ " ações na sua conta.\n\n"
							+ "Até já,\n"
							+ "Caixinha Bet");
			mailSender.send(msg);
			log.info("Verificação de e-mail SMTP enviada para {}", email);
		} catch (Exception e) {
			log.error(
					"Falha ao enviar verificação de e-mail SMTP para {}: {}",
					email,
					e.getMessage(),
					e);
		}
	}
}
