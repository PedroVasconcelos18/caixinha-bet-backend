package com.caxinhabet.pagamento.adapter.notification;

import com.caxinhabet.pagamento.domain.PremioGanhoEmail;
import com.caxinhabet.pagamento.domain.PremioGanhoEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
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

	private static final String CORPO =
			"🎉 BOA! Você é um dos Ganhadores da caixinha '%s'!\n\n"
					+ "Seu prêmio: R$ %s\n\n"
					+ "Entre no app para confirmar sua chave PIX e receber:\n%s\n\n"
					+ "O dinheiro só sai depois que você aceitar — está tudo no seu controle.\n"
					+ "Caixinha Bet";

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
			SimpleMailMessage msg = new SimpleMailMessage();
			msg.setFrom(emailFrom);
			msg.setTo(e.destinatario());
			msg.setSubject("🎉 Você ganhou — " + e.tituloCaixinha());
			msg.setText(
					String.format(
							CORPO, e.tituloCaixinha(), e.valorPremio(), e.linkCaixinha()));
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
