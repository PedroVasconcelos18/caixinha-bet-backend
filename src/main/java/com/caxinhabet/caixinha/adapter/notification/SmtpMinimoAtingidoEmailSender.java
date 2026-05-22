package com.caxinhabet.caixinha.adapter.notification;

import com.caxinhabet.caixinha.domain.MinimoAtingidoEmail;
import com.caxinhabet.caixinha.domain.MinimoAtingidoEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Adapter SMTP do {@link MinimoAtingidoEmailSender} (Story 3.1).
 *
 * <p>Ativo quando {@code caixinha.minimo-atingido.sender=smtp}. Usa
 * {@link JavaMailSender} (configurado por {@code spring.mail.*}).
 *
 * <p>Tom NFR-6: virada/momentum ("hora de pagar"), sem jargão de aposta
 * (guardrail §10). Sem template engine — {@code String.format} basta.
 *
 * <p><b>Falha soft:</b> exceção do {@code JavaMailSender.send} NÃO
 * propaga — Caixinha já está em {@code coletando_pagamentos}; o aviso
 * é best-effort. Padrão idêntico ao {@code SmtpConviteEmailSender}.
 */
@Component
@ConditionalOnProperty(
		prefix = "caixinha.minimo-atingido",
		name = "sender",
		havingValue = "smtp")
public class SmtpMinimoAtingidoEmailSender implements MinimoAtingidoEmailSender {

	private static final Logger log =
			LoggerFactory.getLogger(SmtpMinimoAtingidoEmailSender.class);

	private static final String CORPO_TEMPLATE =
			"✅ Mínimo atingido! A caixinha '%s' (%s) liberou o pagamento.\n\n"
					+ "- Valor do ingresso: %s\n\n"
					+ "Bora pagar? É PIX direto no app — em 1 minuto seu lugar está garantido:\n"
					+ "%s\n\n"
					+ "Lembrete: o dinheiro fica no provedor de pagamento (Asaas), não com a"
					+ " gente. Se a caixinha não der certo, o estorno é automático.\n\n"
					+ "Até já,\n"
					+ "Caixinha Bet";

	private final JavaMailSender mailSender;
	private final String emailFrom;

	public SmtpMinimoAtingidoEmailSender(
			JavaMailSender mailSender,
			@Value("${caixinha.minimo-atingido.email-from:caixinha@caixinha.bet}")
					String emailFrom) {
		this.mailSender = mailSender;
		this.emailFrom = emailFrom;
	}

	@Override
	public void enviarAvisoMinimoAtingido(MinimoAtingidoEmail e) {
		try {
			SimpleMailMessage msg = new SimpleMailMessage();
			msg.setFrom(emailFrom);
			msg.setTo(e.destinatario());
			msg.setSubject("Mínimo atingido! Hora de pagar — " + e.tituloCaixinha());
			msg.setText(
					String.format(
							CORPO_TEMPLATE,
							e.tituloCaixinha(),
							e.confronto(),
							e.valorIngressoFormatado(),
							e.linkCaixinha()));
			mailSender.send(msg);
			log.info(
					"Aviso 'mínimo atingido' SMTP enviado para {} (caixinha={})",
					e.destinatario(),
					e.tituloCaixinha());
		} catch (Exception ex) {
			log.error(
					"Falha SMTP ao enviar 'mínimo atingido' para {} (caixinha={}): {}",
					e.destinatario(),
					e.tituloCaixinha(),
					ex.getMessage(),
					ex);
		}
	}
}
