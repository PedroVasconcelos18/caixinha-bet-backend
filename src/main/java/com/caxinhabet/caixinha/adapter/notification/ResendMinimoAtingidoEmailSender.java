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

	private static final String CORPO_TEMPLATE =
			"✅ Mínimo atingido! A caixinha '%s' (%s) liberou o pagamento.\n\n"
					+ "- Valor do ingresso: %s\n\n"
					+ "Bora pagar? É PIX direto no app — em 1 minuto seu lugar está garantido:\n"
					+ "%s\n\n"
					+ "Lembrete: o dinheiro fica no provedor de pagamento (Asaas), não com a"
					+ " gente. Se a caixinha não der certo, o estorno é automático.\n\n"
					+ "Até já,\n"
					+ "Caixinha Bet";

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
					"Mínimo atingido! Hora de pagar — " + e.tituloCaixinha(),
					String.format(
							CORPO_TEMPLATE,
							e.tituloCaixinha(),
							e.confronto(),
							e.valorIngressoFormatado(),
							e.linkCaixinha()));
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
