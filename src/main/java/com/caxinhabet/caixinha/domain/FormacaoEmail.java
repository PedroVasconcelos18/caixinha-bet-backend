package com.caxinhabet.caixinha.domain;

/**
 * Notificação por e-mail de mudança no estado de Formação da Caixinha
 * (Story 3.4 v5, FR-9 / NFR-6).
 *
 * <p>Cobre os dois eventos:
 * <ul>
 *   <li>{@link Tipo#FORMADA} — a Caixinha atingiu pagamentos suficientes
 *       e Prêmio positivo. Tom de celebração.
 *   <li>{@link Tipo#REVERTIDA} — um estorno derrubou a Caixinha abaixo do
 *       mínimo; ela voltou a coletar pagamentos. Tom de cuidado,
 *       não-acusatório, retificando explicitamente o aviso anterior.
 * </ul>
 *
 * @param destinatario e-mail do Participante.
 * @param tituloCaixinha título da Caixinha.
 * @param confronto rótulo do confronto (ex.: {@code "Brasil × Marrocos"}).
 * @param linkCaixinha URL absoluta para a página da Caixinha.
 * @param tipo qual evento de Formação esta notificação representa.
 */
public record FormacaoEmail(
		String destinatario,
		String tituloCaixinha,
		String confronto,
		String linkCaixinha,
		Tipo tipo) {

	public enum Tipo {
		FORMADA,
		REVERTIDA
	}

	public FormacaoEmail {
		if (destinatario == null || destinatario.isBlank()) {
			throw new IllegalArgumentException("destinatario é obrigatório");
		}
		if (tituloCaixinha == null || tituloCaixinha.isBlank()) {
			throw new IllegalArgumentException("tituloCaixinha é obrigatório");
		}
		if (confronto == null || confronto.isBlank()) {
			throw new IllegalArgumentException("confronto é obrigatório");
		}
		if (linkCaixinha == null || linkCaixinha.isBlank()) {
			throw new IllegalArgumentException("linkCaixinha é obrigatório");
		}
		if (tipo == null) {
			throw new IllegalArgumentException("tipo é obrigatório");
		}
	}
}
