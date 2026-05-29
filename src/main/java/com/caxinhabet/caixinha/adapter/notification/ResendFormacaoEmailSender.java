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

	private static final String CORPO_FORMADA =
			"🏆 É oficial: a caixinha '%s' (%s) está FORMADA!\n\n"
					+ "Pagamentos suficientes confirmados — agora é esperar o jogo.\n"
					+ "Acompanhe tudo aqui:\n%s\n\n"
					+ "Boa sorte a todos!\n"
					+ "Caixinha Bet";

	private static final String CORPO_REVERTIDA =
			"Aviso sobre a caixinha '%s' (%s).\n\n"
					+ "Um pagamento foi estornado e a caixinha voltou a coletar"
					+ " pagamentos — ela ainda NÃO está formada. Aquele aviso de"
					+ " 'Caixinha Formada' que você recebeu antes fica retificado"
					+ " por este.\n\n"
					+ "Nada de errado da sua parte — é só o número de pagamentos"
					+ " confirmados que mudou. Acompanhe aqui:\n%s\n\n"
					+ "Caixinha Bet";

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
			boolean formada = e.tipo() == FormacaoEmail.Tipo.FORMADA;
			resend.enviar(
					emailFrom,
					e.destinatario(),
					(formada ? "🏆 Caixinha formada — " : "Atualização da caixinha — ")
							+ e.tituloCaixinha(),
					String.format(
							formada ? CORPO_FORMADA : CORPO_REVERTIDA,
							e.tituloCaixinha(),
							e.confronto(),
							e.linkCaixinha()));
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
