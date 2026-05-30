package com.caxinhabet.pagamento.adapter.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.caxinhabet.pagamento.domain.PremioGanhoEmail;
import com.caxinhabet.shared.notification.ResendEmailClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ResendPremioGanhoEmailSenderTest {

	private PremioGanhoEmail premio() {
		return new PremioGanhoEmail("ana@x", "Brasil x Marrocos", "150,00", "http://localhost:3000/caixinhas/9");
	}

	@Test
	@DisplayName("HTML e texto trazem o valor do prêmio e o link de aceite")
	void enviaPremio() {
		ResendEmailClient resend = Mockito.mock(ResendEmailClient.class);
		ResendPremioGanhoEmailSender sender = new ResendPremioGanhoEmailSender(resend, "noreply@caixinhabet.com");

		sender.enviarAvisoPremio(premio());

		ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
		verify(resend).enviar(eq("noreply@caixinhabet.com"), eq("ana@x"),
				org.mockito.ArgumentMatchers.contains("ganhou"), html.capture(), texto.capture());

		assertThat(html.getValue()).contains("<html").contains("150,00").contains("http://localhost:3000/caixinhas/9");
		assertThat(texto.getValue()).contains("150,00").contains("http://localhost:3000/caixinhas/9");
	}

	@Test
	@DisplayName("falha do client é soft")
	void falhaSoft() {
		ResendEmailClient resend = Mockito.mock(ResendEmailClient.class);
		doThrow(new RuntimeException("boom")).when(resend)
				.enviar(anyString(), anyString(), anyString(), anyString(), anyString());
		new ResendPremioGanhoEmailSender(resend, "noreply@caixinhabet.com").enviarAvisoPremio(premio());
	}
}
