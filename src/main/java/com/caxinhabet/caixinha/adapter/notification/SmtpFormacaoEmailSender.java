package com.caxinhabet.caixinha.adapter.notification;

import com.caxinhabet.caixinha.domain.FormacaoEmail;
import com.caxinhabet.caixinha.domain.FormacaoEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Adapter SMTP do {@link FormacaoEmailSender} (Story 3.4).
 *
 * <p>Ativo quando {@code caixinha.formacao.sender=smtp}. Conteúdo difere
 * por {@link FormacaoEmail.Tipo}: celebração na formação, cuidado
 * não-acusatório na reversão (NFR-6 / addendum A6).
 *
 * <p><b>Falha soft:</b> exceção do {@code send} NÃO propaga — a transição
 * de Formação já está no DB; aviso é best-effort.
 */
@Component
@ConditionalOnProperty(
		prefix = "caixinha.formacao",
		name = "sender",
		havingValue = "smtp")
public class SmtpFormacaoEmailSender implements FormacaoEmailSender {

	private static final Logger log =
			LoggerFactory.getLogger(SmtpFormacaoEmailSender.class);

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

	private final JavaMailSender mailSender;
	private final String emailFrom;

	public SmtpFormacaoEmailSender(
			JavaMailSender mailSender,
			@Value("${caixinha.formacao.email-from:noreply@caixinhabet.com}")
					String emailFrom) {
		this.mailSender = mailSender;
		this.emailFrom = emailFrom;
	}

	@Override
	public void enviarAvisoFormacao(FormacaoEmail e) {
		try {
			SimpleMailMessage msg = new SimpleMailMessage();
			msg.setFrom(emailFrom);
			msg.setTo(e.destinatario());
			boolean formada = e.tipo() == FormacaoEmail.Tipo.FORMADA;
			msg.setSubject(
					(formada ? "🏆 Caixinha formada — " : "Atualização da caixinha — ")
							+ e.tituloCaixinha());
			msg.setText(
					String.format(
							formada ? CORPO_FORMADA : CORPO_REVERTIDA,
							e.tituloCaixinha(),
							e.confronto(),
							e.linkCaixinha()));
			mailSender.send(msg);
			log.info(
					"Aviso de Formação ({}) SMTP enviado para {} (caixinha={})",
					e.tipo(),
					e.destinatario(),
					e.tituloCaixinha());
		} catch (Exception ex) {
			log.error(
					"Falha SMTP ao enviar aviso de Formação para {} (caixinha={}): {}",
					e.destinatario(),
					e.tituloCaixinha(),
					ex.getMessage(),
					ex);
		}
	}
}
