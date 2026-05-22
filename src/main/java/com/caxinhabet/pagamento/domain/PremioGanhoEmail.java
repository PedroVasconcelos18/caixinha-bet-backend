package com.caxinhabet.pagamento.domain;

/**
 * Notificação por e-mail enviada a um Ganhador no momento da apuração
 * (Épico 4 v5, Story 4.3, FR-13 / NFR-6).
 *
 * <p>"Você ganhou R$ X — entre no app para receber." Tom de festa
 * (addendum A6). O Ganhador precisa entrar no app e aceitar (Story 4.6)
 * para o PIX ser disparado.
 *
 * @param destinatario e-mail do Ganhador.
 * @param tituloCaixinha título da Caixinha.
 * @param valorPremio valor que o Ganhador receberá, string decimal (Money).
 * @param linkCaixinha URL absoluta para a Caixinha (onde ele aceita).
 */
public record PremioGanhoEmail(
		String destinatario,
		String tituloCaixinha,
		String valorPremio,
		String linkCaixinha) {

	public PremioGanhoEmail {
		if (destinatario == null || destinatario.isBlank()) {
			throw new IllegalArgumentException("destinatario é obrigatório");
		}
		if (tituloCaixinha == null || tituloCaixinha.isBlank()) {
			throw new IllegalArgumentException("tituloCaixinha é obrigatório");
		}
		if (valorPremio == null || valorPremio.isBlank()) {
			throw new IllegalArgumentException("valorPremio é obrigatório");
		}
		if (linkCaixinha == null || linkCaixinha.isBlank()) {
			throw new IllegalArgumentException("linkCaixinha é obrigatório");
		}
	}
}
