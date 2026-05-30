package com.caxinhabet.caixinha.adapter.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.caxinhabet.caixinha.domain.MinimoAtingidoEmail;
import com.caxinhabet.shared.notification.ResendEmailClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ResendMinimoAtingidoEmailSenderTest {

	private MinimoAtingidoEmail evento() {
		return new MinimoAtingidoEmail(
				"alice@local", "Brasil x Marrocos", "Brasil x Marrocos",
				"R$ 40,00", "http://localhost:3000/caixinhas/9");
	}

	@Test
	@DisplayName("HTML e texto carregam o valor, o link e o aviso Asaas (tom NFR-6)")
	void enviaConteudoCorreto() {
		ResendEmailClient resend = Mockito.mock(ResendEmailClient.class);
		ResendMinimoAtingidoEmailSender sender =
				new ResendMinimoAtingidoEmailSender(resend, "noreply@caixinhabet.com");

		sender.enviarAvisoMinimoAtingido(evento());

		ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
		verify(resend).enviar(
				eq("noreply@caixinhabet.com"), eq("alice@local"),
				eq("Mínimo atingido! Hora de pagar — Brasil x Marrocos"),
				html.capture(), texto.capture());

		assertThat(html.getValue())
				.contains("<html").contains("R$ 40,00")
				.contains("http://localhost:3000/caixinhas/9");
		assertThat(texto.getValue())
				.contains("R$ 40,00")
				.contains("http://localhost:3000/caixinhas/9")
				.contains("Asaas")
				.doesNotContain("aposta").doesNotContain("odd");
	}

	@Test
	@DisplayName("falha do client é soft")
	void falhaSoft() {
		ResendEmailClient resend = Mockito.mock(ResendEmailClient.class);
		doThrow(new RuntimeException("boom")).when(resend)
				.enviar(anyString(), anyString(), anyString(), anyString(), anyString());
		new ResendMinimoAtingidoEmailSender(resend, "noreply@caixinhabet.com")
				.enviarAvisoMinimoAtingido(evento());
	}
}
