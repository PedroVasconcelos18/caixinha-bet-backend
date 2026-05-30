package com.caxinhabet.pagamento.adapter.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.caxinhabet.pagamento.domain.CancelamentoEmail;
import com.caxinhabet.shared.notification.ResendEmailClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ResendCancelamentoEmailSenderTest {

	@Test
	@DisplayName("com pagamento: callout de devolução automática (tom não-punitivo)")
	void comPagamento() {
		ResendEmailClient resend = Mockito.mock(ResendEmailClient.class);
		ResendCancelamentoEmailSender sender =
				new ResendCancelamentoEmailSender(resend, "noreply@caixinhabet.com");

		sender.enviarAvisoCancelamento(
				new CancelamentoEmail("ana@x", "Brasil x Marrocos", true));

		ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
		verify(resend).enviar(
				eq("noreply@caixinhabet.com"), eq("ana@x"),
				eq("A caixinha 'Brasil x Marrocos' não fechou"),
				html.capture(), texto.capture());

		assertThat(html.getValue()).contains("<html").contains("devolvendo seu dinheiro");
		assertThat(texto.getValue())
				.contains("devolvendo seu dinheiro")
				.contains("não precisa fazer nada")
				.doesNotContain("não pagou");
	}

	@Test
	@DisplayName("sem pagamento: só informa o não-fechamento (sem reembolso)")
	void semPagamento() {
		ResendEmailClient resend = Mockito.mock(ResendEmailClient.class);
		ResendCancelamentoEmailSender sender =
				new ResendCancelamentoEmailSender(resend, "noreply@caixinhabet.com");

		sender.enviarAvisoCancelamento(
				new CancelamentoEmail("bob@x", "Brasil x Marrocos", false));

		ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
		verify(resend).enviar(
				eq("noreply@caixinhabet.com"), eq("bob@x"),
				eq("A caixinha 'Brasil x Marrocos' não fechou"),
				html.capture(), texto.capture());

		assertThat(html.getValue()).contains("<html").contains("não há nada a devolver");
		assertThat(texto.getValue())
				.contains("não há nada a devolver")
				.doesNotContain("devolvendo seu dinheiro");
	}

	@Test
	@DisplayName("falha do client é soft")
	void falhaSoft() {
		ResendEmailClient resend = Mockito.mock(ResendEmailClient.class);
		doThrow(new RuntimeException("boom")).when(resend)
				.enviar(anyString(), anyString(), anyString(), anyString(), anyString());
		new ResendCancelamentoEmailSender(resend, "noreply@caixinhabet.com")
				.enviarAvisoCancelamento(new CancelamentoEmail("ana@x", "Brasil x Marrocos", true));
	}
}
