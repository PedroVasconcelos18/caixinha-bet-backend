package com.caxinhabet.caixinha.adapter.notification;

import com.caxinhabet.caixinha.domain.MinimoAtingidoEmail;
import com.caxinhabet.caixinha.domain.MinimoAtingidoEmailSender;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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

	private final JavaMailSender mailSender;
	private final String emailFrom;

	public SmtpMinimoAtingidoEmailSender(
			JavaMailSender mailSender,
			@Value("${caixinha.minimo-atingido.email-from:noreply@caixinhabet.com}")
					String emailFrom) {
		this.mailSender = mailSender;
		this.emailFrom = emailFrom;
	}

	@Override
	public void enviarAvisoMinimoAtingido(MinimoAtingidoEmail e) {
		try {
			MimeMessage msg = mailSender.createMimeMessage();
			MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
			h.setFrom(emailFrom);
			h.setTo(e.destinatario());
			h.setSubject(MinimoAtingidoEmails.assunto(e));
			h.setText(MinimoAtingidoEmails.texto(e), MinimoAtingidoEmails.html(e));
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
