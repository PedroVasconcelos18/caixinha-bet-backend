package com.caxinhabet.caixinha.adapter.notification;

import com.caxinhabet.caixinha.domain.ConviteEmail;
import com.caxinhabet.caixinha.domain.ConviteEmailSender;
import com.caxinhabet.shared.notification.ResendEmailClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adapter HTTP (Resend) do {@link ConviteEmailSender}.
 *
 * <p>Ativo quando {@code caixinha.convite.sender=resend}. Usa a API HTTP do
 * Resend (443) em vez de SMTP. Mesmo conteúdo do
 * {@link SmtpConviteEmailSender}; falha soft.
 */
@Component
@ConditionalOnProperty(
		prefix = "caixinha.convite",
		name = "sender",
		havingValue = "resend")
public class ResendConviteEmailSender implements ConviteEmailSender {

	private static final Logger log = LoggerFactory.getLogger(ResendConviteEmailSender.class);

	private static final String CORPO_TEMPLATE =
			"Oi! O %s montou uma caixinha do jogo %s e te convidou para participar.\n\n"
					+ "- Valor do ingresso: %s\n"
					+ "- Caixinha: %s\n\n"
					+ "Aceita o convite e escolhe seu palpite:\n"
					+ "%s\n\n"
					+ "Ah, importante: este é um bolão entre amigos. O dinheiro fica num"
					+ " provedor de pagamento licenciado (Asaas), nunca com a gente. Se"
					+ " a caixinha não der certo, o estorno é automático.\n\n"
					+ "Até já,\n"
					+ "Caixinha Bet";

	private final ResendEmailClient resend;
	private final String emailFrom;

	public ResendConviteEmailSender(
			ResendEmailClient resend,
			@Value("${caixinha.convite.email-from:noreply@caixinhabet.com}") String emailFrom) {
		this.resend = resend;
		this.emailFrom = emailFrom;
	}

	@Override
	public void enviarConvite(ConviteEmail c) {
		try {
			resend.enviar(
					emailFrom,
					c.destinatario(),
					c.organizadorNome() + " te chamou para uma caixinha!",
					String.format(
							CORPO_TEMPLATE,
							c.organizadorNome(),
							c.confronto(),
							c.valorIngressoFormatado(),
							c.tituloCaixinha(),
							c.linkConvite()));
			log.info("Convite Resend enviado para {}", c.destinatario());
		} catch (Exception e) {
			log.error(
					"Falha ao enviar convite Resend para {} (caixinha={}): {}",
					c.destinatario(),
					c.tituloCaixinha(),
					e.getMessage(),
					e);
		}
	}
}
