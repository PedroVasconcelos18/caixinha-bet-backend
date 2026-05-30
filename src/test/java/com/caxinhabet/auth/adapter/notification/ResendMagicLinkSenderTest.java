package com.caxinhabet.auth.adapter.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.caxinhabet.shared.notification.ResendEmailClient;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** ResendMagicLinkSender com o client HTTP mockado (HTML + texto). */
class ResendMagicLinkSenderTest {

	@Test
	@DisplayName("enviar() manda HTML e texto com o link e o assunto de hoje")
	void enviaAcesso() {
		ResendEmailClient resend = Mockito.mock(ResendEmailClient.class);
		ResendMagicLinkSender sender = new ResendMagicLinkSender(resend, "noreply@caixinhabet.com");

		sender.enviar("pedro@x", "https://app/auth/callback?token=T", Instant.now());

		ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
		verify(resend).enviar(
				eq("noreply@caixinhabet.com"),
				eq("pedro@x"),
				eq("Seu link de acesso ao Caixinha Bet"),
				html.capture(),
				texto.capture());

		assertThat(html.getValue())
				.contains("<html").contains("CAIXINHA")
				.contains("https://app/auth/callback?token=T");
		assertThat(texto.getValue())
				.contains("https://app/auth/callback?token=T")
				.contains("15 minutos");
	}

	@Test
	@DisplayName("enviarVerificacao() usa o assunto de verificação")
	void enviaVerificacao() {
		ResendEmailClient resend = Mockito.mock(ResendEmailClient.class);
		ResendMagicLinkSender sender = new ResendMagicLinkSender(resend, "noreply@caixinhabet.com");

		sender.enviarVerificacao("pedro@x", "https://app/verificar-email?token=T", Instant.now());

		verify(resend).enviar(
				eq("noreply@caixinhabet.com"), eq("pedro@x"),
				eq("Confirme seu e-mail no Caixinha Bet"), anyString(), anyString());
	}

	@Test
	@DisplayName("falha do client é absorvida (soft)")
	void falhaSoft() {
		ResendEmailClient resend = Mockito.mock(ResendEmailClient.class);
		doThrow(new RuntimeException("boom")).when(resend)
				.enviar(anyString(), anyString(), anyString(), anyString(), anyString());
		ResendMagicLinkSender sender = new ResendMagicLinkSender(resend, "noreply@caixinhabet.com");
		// NÃO deve lançar
		sender.enviar("pedro@x", "https://app/x", Instant.now());
	}
}
