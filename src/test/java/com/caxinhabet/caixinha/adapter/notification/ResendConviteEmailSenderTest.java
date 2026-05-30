package com.caxinhabet.caixinha.adapter.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.caxinhabet.caixinha.domain.ConviteEmail;
import com.caxinhabet.shared.notification.ResendEmailClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ResendConviteEmailSenderTest {

	private ConviteEmail convite() {
		return new ConviteEmail(
				"alice@local", "Rafael", "Brasil x Marrocos", "Brasil x Marrocos",
				"R$ 40,00", "http://localhost:3000/convites/42");
	}

	@Test
	@DisplayName("HTML e texto carregam o conteúdo e o tom NFR-6 (sem jargão de aposta)")
	void enviaConteudoCorreto() {
		ResendEmailClient resend = Mockito.mock(ResendEmailClient.class);
		ResendConviteEmailSender sender = new ResendConviteEmailSender(resend, "noreply@caixinhabet.com");

		sender.enviarConvite(convite());

		ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
		verify(resend).enviar(
				eq("noreply@caixinhabet.com"), eq("alice@local"),
				org.mockito.ArgumentMatchers.contains("caixinha"),
				html.capture(), texto.capture());

		assertThat(html.getValue())
				.contains("<html").contains("Rafael").contains("R$ 40,00")
				.contains("http://localhost:3000/convites/42");
		assertThat(texto.getValue())
				.contains("Rafael").contains("R$ 40,00")
				.contains("provedor de pagamento licenciado")
				.doesNotContain("aposta").doesNotContain("odd");
	}

	@Test
	@DisplayName("falha do client é soft")
	void falhaSoft() {
		ResendEmailClient resend = Mockito.mock(ResendEmailClient.class);
		doThrow(new RuntimeException("boom")).when(resend)
				.enviar(anyString(), anyString(), anyString(), anyString(), anyString());
		new ResendConviteEmailSender(resend, "noreply@caixinhabet.com").enviarConvite(convite());
	}
}
