package com.caxinhabet.pagamento.adapter.notification;

import com.caxinhabet.pagamento.domain.CancelamentoEmail;
import com.caxinhabet.pagamento.domain.CancelamentoEmailSender;
import com.caxinhabet.shared.notification.ResendEmailClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adapter HTTP (Resend) do {@link CancelamentoEmailSender}.
 *
 * <p>Ativo quando {@code caixinha.cancelamento.sender=resend}. Mesmo
 * conteúdo do {@link SmtpCancelamentoEmailSender} (tom de cuidado,
 * não-punitivo); falha soft.
 */
@Component
@ConditionalOnProperty(
		prefix = "caixinha.cancelamento",
		name = "sender",
		havingValue = "resend")
public class ResendCancelamentoEmailSender implements CancelamentoEmailSender {

	private static final Logger log =
			LoggerFactory.getLogger(ResendCancelamentoEmailSender.class);

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

	private final ResendEmailClient resend;
	private final String emailFrom;

	public ResendCancelamentoEmailSender(
			ResendEmailClient resend,
			@Value("${caixinha.cancelamento.email-from:noreply@caixinhabet.com}")
					String emailFrom) {
		this.resend = resend;
		this.emailFrom = emailFrom;
	}

	@Override
	public void enviarAvisoCancelamento(CancelamentoEmail e) {
		try {
			resend.enviar(
					emailFrom,
					e.destinatario(),
					"A caixinha '" + e.tituloCaixinha() + "' não fechou",
					String.format(
							e.houvePagamento() ? CORPO_COM_PAGAMENTO : CORPO_SEM_PAGAMENTO,
							e.tituloCaixinha()));
			log.info(
					"Aviso de cancelamento Resend enviado para {} (caixinha={})",
					e.destinatario(),
					e.tituloCaixinha());
		} catch (Exception ex) {
			log.error(
					"Falha ao enviar aviso de cancelamento Resend para {} (caixinha={}): {}",
					e.destinatario(),
					e.tituloCaixinha(),
					ex.getMessage(),
					ex);
		}
	}
}
