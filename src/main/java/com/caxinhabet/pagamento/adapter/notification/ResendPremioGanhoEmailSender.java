package com.caxinhabet.pagamento.adapter.notification;

import com.caxinhabet.pagamento.domain.PremioGanhoEmail;
import com.caxinhabet.pagamento.domain.PremioGanhoEmailSender;
import com.caxinhabet.shared.notification.ResendEmailClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adapter HTTP (Resend) do {@link PremioGanhoEmailSender}.
 *
 * <p>Ativo quando {@code caixinha.premio-ganho.sender=resend}. Mesmo
 * conteúdo do {@link SmtpPremioGanhoEmailSender}; falha soft.
 */
@Component
@ConditionalOnProperty(
		prefix = "caixinha.premio-ganho",
		name = "sender",
		havingValue = "resend")
public class ResendPremioGanhoEmailSender implements PremioGanhoEmailSender {

	private static final Logger log =
			LoggerFactory.getLogger(ResendPremioGanhoEmailSender.class);

	private static final String CORPO =
			"🎉 BOA! Você é um dos Ganhadores da caixinha '%s'!\n\n"
					+ "Seu prêmio: R$ %s\n\n"
					+ "Entre no app para confirmar sua chave PIX e receber:\n%s\n\n"
					+ "O dinheiro só sai depois que você aceitar — está tudo no seu controle.\n"
					+ "Caixinha Bet";

	private final ResendEmailClient resend;
	private final String emailFrom;

	public ResendPremioGanhoEmailSender(
			ResendEmailClient resend,
			@Value("${caixinha.premio-ganho.email-from:noreply@caixinhabet.com}")
					String emailFrom) {
		this.resend = resend;
		this.emailFrom = emailFrom;
	}

	@Override
	public void enviarAvisoPremio(PremioGanhoEmail e) {
		try {
			resend.enviar(
					emailFrom,
					e.destinatario(),
					"🎉 Você ganhou — " + e.tituloCaixinha(),
					String.format(
							CORPO, e.tituloCaixinha(), e.valorPremio(), e.linkCaixinha()));
			log.info(
					"Aviso de prêmio Resend enviado para {} (caixinha={})",
					e.destinatario(),
					e.tituloCaixinha());
		} catch (Exception ex) {
			log.error(
					"Falha ao enviar aviso de prêmio Resend para {} (caixinha={}): {}",
					e.destinatario(),
					e.tituloCaixinha(),
					ex.getMessage(),
					ex);
		}
	}
}
