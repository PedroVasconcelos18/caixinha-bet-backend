package com.caxinhabet.pagamento.adapter.notification;

import com.caxinhabet.pagamento.domain.CancelamentoEmail;
import com.caxinhabet.pagamento.domain.CancelamentoEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Adapter SMTP do {@link CancelamentoEmailSender} (Story 5.1).
 *
 * <p>Ativo quando {@code caixinha.cancelamento.sender=smtp}. Tom de
 * cuidado, não-punitivo (NFR-6) — nunca culpa ninguém. Falha de envio
 * NÃO propaga — best-effort.
 */
@Component
@ConditionalOnProperty(
		prefix = "caixinha.cancelamento",
		name = "sender",
		havingValue = "smtp")
public class SmtpCancelamentoEmailSender implements CancelamentoEmailSender {

	private static final Logger log =
			LoggerFactory.getLogger(SmtpCancelamentoEmailSender.class);

	private static final String CORPO_COM_PAGAMENTO =
			"A caixinha '%s' não fechou — não atingiu o número mínimo de"
					+ " participantes no prazo.\n\n"
					+ "Você pagou o ingresso, então já estamos devolvendo seu dinheiro"
					+ " automaticamente, valor cheio, direto na origem do pagamento."
					+ " Você não precisa fazer nada.\n\n"
					+ "Acontece — quem sabe na próxima!\n"
					+ "Caixinha Bet";

	private static final String CORPO_SEM_PAGAMENTO =
			"A caixinha '%s' não fechou — não atingiu o número mínimo de"
					+ " participantes no prazo.\n\n"
					+ "Como você ainda não tinha pago o ingresso, não há nada a"
					+ " devolver. Fica para a próxima!\n\n"
					+ "Caixinha Bet";

	private final JavaMailSender mailSender;
	private final String emailFrom;

	public SmtpCancelamentoEmailSender(
			JavaMailSender mailSender,
			@Value("${caixinha.cancelamento.email-from:noreply@caixinhabet.com}")
					String emailFrom) {
		this.mailSender = mailSender;
		this.emailFrom = emailFrom;
	}

	@Override
	public void enviarAvisoCancelamento(CancelamentoEmail e) {
		try {
			SimpleMailMessage msg = new SimpleMailMessage();
			msg.setFrom(emailFrom);
			msg.setTo(e.destinatario());
			msg.setSubject("A caixinha '" + e.tituloCaixinha() + "' não fechou");
			msg.setText(
					String.format(
							e.houvePagamento()
									? CORPO_COM_PAGAMENTO
									: CORPO_SEM_PAGAMENTO,
							e.tituloCaixinha()));
			mailSender.send(msg);
			log.info(
					"Aviso de cancelamento SMTP enviado para {} (caixinha={})",
					e.destinatario(),
					e.tituloCaixinha());
		} catch (Exception ex) {
			log.error(
					"Falha SMTP ao enviar aviso de cancelamento para {} (caixinha={}): {}",
					e.destinatario(),
					e.tituloCaixinha(),
					ex.getMessage(),
					ex);
		}
	}
}
