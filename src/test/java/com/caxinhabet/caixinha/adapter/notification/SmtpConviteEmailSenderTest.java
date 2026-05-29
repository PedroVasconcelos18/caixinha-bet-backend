package com.caxinhabet.caixinha.adapter.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.caxinhabet.caixinha.domain.ConviteEmail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Story 2.4 — SmtpConviteEmailSender com {@link JavaMailSender} mockado.
 * Não sobe SMTP real (CI não tem servidor).
 */
class SmtpConviteEmailSenderTest {

	@Test
	@DisplayName("send é chamado com from, to, subject e body corretos (tom NFR-6)")
	void enviaCorretamente() {
		JavaMailSender mailSender = Mockito.mock(JavaMailSender.class);
		SmtpConviteEmailSender sender = new SmtpConviteEmailSender(mailSender, "noreply@caixinhabet.com");

		ConviteEmail c =
				new ConviteEmail(
						"alice@local",
						"rafael@local",
						"Brasil x Marrocos",
						"Brasil x Marrocos",
						"R$ 40.00",
						"http://localhost:3000/convites/42");
		sender.enviarConvite(c);

		ArgumentCaptor<SimpleMailMessage> captor =
				ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender, times(1)).send(captor.capture());

		SimpleMailMessage msg = captor.getValue();
		assertThat(msg.getFrom()).isEqualTo("noreply@caixinhabet.com");
		assertThat(msg.getTo()).containsExactly("alice@local");
		assertThat(msg.getSubject()).contains("rafael@local").contains("caixinha");
		assertThat(msg.getText())
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
		JavaMailSender mailSender = Mockito.mock(JavaMailSender.class);
		doThrow(new RuntimeException("SMTP fora do ar"))
				.when(mailSender)
				.send(any(SimpleMailMessage.class));
		SmtpConviteEmailSender sender = new SmtpConviteEmailSender(mailSender, "noreply@caixinhabet.com");

		ConviteEmail c =
				new ConviteEmail(
						"alice@local",
						"rafael@local",
						"Brasil x Marrocos",
						"Brasil x Marrocos",
						"R$ 40.00",
						"http://localhost:3000/convites/42");
		// NÃO deve lançar
		sender.enviarConvite(c);
	}
}
