package com.caxinhabet.caixinha.adapter.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.caxinhabet.caixinha.domain.FormacaoEmail;
import com.caxinhabet.shared.notification.ResendEmailClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ResendFormacaoEmailSenderTest {

	private FormacaoEmail evento(FormacaoEmail.Tipo tipo) {
		return new FormacaoEmail(
				"alice@local", "Brasil x Marrocos", "Brasil x Marrocos",
				"http://localhost:3000/caixinhas/9", tipo);
	}

	@Test
	@DisplayName("FORMADA: assunto e HTML/texto de celebração com o link")
	void formada() {
		ResendEmailClient resend = Mockito.mock(ResendEmailClient.class);
		ResendFormacaoEmailSender sender =
				new ResendFormacaoEmailSender(resend, "noreply@caixinhabet.com");

		sender.enviarAvisoFormacao(evento(FormacaoEmail.Tipo.FORMADA));

		ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
		verify(resend).enviar(
				eq("noreply@caixinhabet.com"), eq("alice@local"),
				eq("🏆 Caixinha formada — Brasil x Marrocos"),
				html.capture(), texto.capture());

		assertThat(html.getValue())
				.contains("<html").contains("formada")
				.contains("http://localhost:3000/caixinhas/9");
		assertThat(texto.getValue())
				.contains("FORMADA")
				.contains("http://localhost:3000/caixinhas/9")
				.doesNotContain("aposta");
	}

	@Test
	@DisplayName("REVERTIDA: assunto neutro e tom de cuidado (retifica aviso anterior)")
	void revertida() {
		ResendEmailClient resend = Mockito.mock(ResendEmailClient.class);
		ResendFormacaoEmailSender sender =
				new ResendFormacaoEmailSender(resend, "noreply@caixinhabet.com");

		sender.enviarAvisoFormacao(evento(FormacaoEmail.Tipo.REVERTIDA));

		ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
		verify(resend).enviar(
				eq("noreply@caixinhabet.com"), eq("alice@local"),
				eq("Atualização da caixinha — Brasil x Marrocos"),
				html.capture(), texto.capture());

		assertThat(html.getValue()).contains("<html").contains("retificado");
		assertThat(texto.getValue())
				.contains("retificado")
				.contains("Nada de errado da sua parte")
				.doesNotContain("aposta");
	}

	@Test
	@DisplayName("falha do client é soft")
	void falhaSoft() {
		ResendEmailClient resend = Mockito.mock(ResendEmailClient.class);
		doThrow(new RuntimeException("boom")).when(resend)
				.enviar(anyString(), anyString(), anyString(), anyString(), anyString());
		new ResendFormacaoEmailSender(resend, "noreply@caixinhabet.com")
				.enviarAvisoFormacao(evento(FormacaoEmail.Tipo.FORMADA));
	}
}
