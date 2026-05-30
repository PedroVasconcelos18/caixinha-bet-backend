package com.caxinhabet.shared.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Helper de template HTML compartilhado — chrome da marca + blocos. */
class EmailTemplateTest {

	@Test
	@DisplayName("pagina() envolve o conteúdo no documento da marca")
	void paginaEnvolveConteudo() {
		String html = EmailTemplate.pagina("Preview de teste", "<p>OLÁ-CORPO</p>");

		assertThat(html)
				.contains("<!DOCTYPE html>")
				.contains("<title>Preview de teste</title>")
				.contains("OLÁ-CORPO")
				// marca: "CAIXINHA" + "BET" (BET destacado em verde)
				.contains("CAIXINHA")
				.contains("BET")
				// rodapé +18 (guardrail PRD §10 / Footer.tsx)
				.contains("+18")
				// tema escuro (cor de fundo do design)
				.contains("#070b14");
	}

	@Test
	@DisplayName("botao() rende o href, o label e o gradiente verde")
	void botaoRendeLinkELabel() {
		String html = EmailTemplate.botao("Aceitar convite", "https://x/y?z=1");
		assertThat(html)
				.contains("https://x/y?z=1")
				.contains("Aceitar convite")
				.contains("#1fe074"); // verde da marca
	}

	@Test
	@DisplayName("escape() neutraliza caracteres HTML de dados do usuário")
	void escapeNeutralizaHtml() {
		assertThat(EmailTemplate.escape("<b>A & \"B\"</b>"))
				.isEqualTo("&lt;b&gt;A &amp; &quot;B&quot;&lt;/b&gt;")
				.doesNotContain("<b>");
	}

	@Test
	@DisplayName("callout(WARN) usa o tom âmbar do design")
	void calloutWarnEhAmbar() {
		String html = EmailTemplate.callout(EmailTemplate.Tom.WARN, "cuidado");
		assertThat(html).contains("cuidado").contains("#f5a623");
	}

	@Test
	@DisplayName("infoLinha() rende rótulo e valor")
	void infoLinhaRendeRotuloValor() {
		String html = EmailTemplate.infoLinha("Valor do ingresso", "R$ 40,00");
		assertThat(html).contains("Valor do ingresso").contains("R$ 40,00");
	}
}
