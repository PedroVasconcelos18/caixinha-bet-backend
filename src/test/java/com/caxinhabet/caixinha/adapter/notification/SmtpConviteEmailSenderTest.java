package com.caxinhabet.caixinha.adapter.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.caxinhabet.caixinha.domain.ConviteEmail;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Story 2.4 — SmtpConviteEmailSender com {@link JavaMailSender} mockado.
 * Não sobe SMTP real (CI não tem servidor). Usa um {@link JavaMailSenderImpl}
 * real só para criar a {@link MimeMessage} (não conecta).
 */
class SmtpConviteEmailSenderTest {

	private static ConviteEmail convite() {
		return new ConviteEmail(
				"alice@local",
				"rafael@local",
				"Brasil x Marrocos",
				"Brasil x Marrocos",
				"R$ 40.00",
				"http://localhost:3000/convites/42");
	}

	private static String conteudoComoTexto(MimeMessage msg) throws Exception {
		return parte(msg.getContent());
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
	@DisplayName("send é chamado com from, to, subject e body corretos (tom NFR-6)")
	void enviaCorretamente() throws Exception {
		JavaMailSenderImpl impl = new JavaMailSenderImpl();
		JavaMailSender mailSender = Mockito.mock(JavaMailSender.class);
		Mockito.when(mailSender.createMimeMessage()).thenReturn(impl.createMimeMessage());

		SmtpConviteEmailSender sender = new SmtpConviteEmailSender(mailSender, "noreply@caixinhabet.com");
		sender.enviarConvite(convite());

		ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
		verify(mailSender, times(1)).send(captor.capture());

		MimeMessage msg = captor.getValue();
		assertThat(msg.getFrom()[0].toString()).isEqualTo("noreply@caixinhabet.com");
		assertThat(msg.getAllRecipients()[0].toString()).isEqualTo("alice@local");
		assertThat(msg.getSubject()).contains("rafael@local").contains("caixinha");

		String corpo = conteudoComoTexto(msg);
		assertThat(corpo)
				.contains("Brasil x Marrocos")
				.contains("R$ 40.00")
				.contains("http://localhost:3000/convites/42")
				.contains("provedor de pagamento licenciado")
				.doesNotContain("aposta")
				.doesNotContain("odd");
	}

	@Test
	@DisplayName("Falha do mailSender é absorvida (não propaga ao caller — best-effort)")
	void falhaSmtpEhSoft() {
		JavaMailSenderImpl impl = new JavaMailSenderImpl();
		JavaMailSender mailSender = Mockito.mock(JavaMailSender.class);
		Mockito.when(mailSender.createMimeMessage()).thenReturn(impl.createMimeMessage());
		doThrow(new RuntimeException("SMTP fora do ar"))
				.when(mailSender)
				.send(any(MimeMessage.class));

		SmtpConviteEmailSender sender = new SmtpConviteEmailSender(mailSender, "noreply@caixinhabet.com");
		// NÃO deve lançar
		sender.enviarConvite(convite());
	}
}
