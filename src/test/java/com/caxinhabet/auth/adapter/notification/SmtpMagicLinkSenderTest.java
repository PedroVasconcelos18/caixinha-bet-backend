package com.caxinhabet.auth.adapter.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * SmtpMagicLinkSender com {@link JavaMailSender} mockado. Usa um
 * {@link JavaMailSenderImpl} real só para criar a {@link MimeMessage}
 * (não conecta); o {@code send} é verificado.
 */
class SmtpMagicLinkSenderTest {

	private static String conteudoComoTexto(MimeMessage msg) throws Exception {
		Object content = msg.getContent();
		if (content instanceof MimeMultipart mp) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < mp.getCount(); i++) {
				sb.append(parte(mp.getBodyPart(i).getContent()));
			}
			return sb.toString();
		}
		return String.valueOf(content);
	}

	private static String parte(Object content) throws Exception {
		if (content instanceof MimeMultipart mp) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < mp.getCount(); i++) {
				sb.append(parte(mp.getBodyPart(i).getContent()));
			}
			return sb.toString();
		}
		return String.valueOf(content);
	}

	@Test
	@DisplayName("enviar() monta MimeMessage com assunto, texto e HTML do acesso")
	void enviaAcesso() throws Exception {
		JavaMailSenderImpl impl = new JavaMailSenderImpl();
		JavaMailSender mailSender = Mockito.mock(JavaMailSender.class);
		Mockito.when(mailSender.createMimeMessage()).thenReturn(impl.createMimeMessage());

		SmtpMagicLinkSender sender = new SmtpMagicLinkSender(mailSender, "noreply@caixinhabet.com");
		sender.enviar("pedro@x", "http://localhost:3000/auth/callback?token=T", Instant.now());

		ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
		verify(mailSender).send(captor.capture());
		MimeMessage msg = captor.getValue();

		assertThat(msg.getSubject()).isEqualTo("Seu link de acesso ao Caixinha Bet");
		String corpo = conteudoComoTexto(msg);
		assertThat(corpo)
				.contains("http://localhost:3000/auth/callback?token=T")
				.contains("15 minutos")
				.contains("CAIXINHA"); // HTML da marca
	}

	@Test
	@DisplayName("enviarVerificacao() usa o assunto de verificação")
	void enviaVerificacao() throws Exception {
		JavaMailSenderImpl impl = new JavaMailSenderImpl();
		JavaMailSender mailSender = Mockito.mock(JavaMailSender.class);
		Mockito.when(mailSender.createMimeMessage()).thenReturn(impl.createMimeMessage());

		SmtpMagicLinkSender sender = new SmtpMagicLinkSender(mailSender, "noreply@caixinhabet.com");
		sender.enviarVerificacao("pedro@x", "http://localhost:3000/verificar-email?token=T", Instant.now());

		ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
		verify(mailSender).send(captor.capture());
		assertThat(captor.getValue().getSubject()).isEqualTo("Confirme seu e-mail no Caixinha Bet");
	}

	@Test
	@DisplayName("falha do mailSender é absorvida (soft)")
	void falhaSoft() {
		JavaMailSenderImpl impl = new JavaMailSenderImpl();
		JavaMailSender mailSender = Mockito.mock(JavaMailSender.class);
		Mockito.when(mailSender.createMimeMessage()).thenReturn(impl.createMimeMessage());
		doThrow(new RuntimeException("SMTP fora")).when(mailSender).send(any(MimeMessage.class));

		SmtpMagicLinkSender sender = new SmtpMagicLinkSender(mailSender, "noreply@caixinhabet.com");
		// NÃO deve lançar
		sender.enviar("pedro@x", "http://localhost:3000/x", Instant.now());
	}
}
