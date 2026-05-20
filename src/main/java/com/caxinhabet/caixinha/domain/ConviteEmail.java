package com.caxinhabet.caixinha.domain;

/**
 * Dados de um convite a enviar por e-mail (Story 2.4, FR-4).
 *
 * <p>Record imutável carregando exatamente o que o template precisa.
 * Construção centraliza a montagem (use case); o adapter consome.
 *
 * <p>Tom NFR-6: campos em linguagem amigável ({@code organizadorNome},
 * {@code tituloCaixinha}, {@code confronto}) — não "subject template id"
 * nem "campaign code".
 */
public record ConviteEmail(
		String destinatario,
		String organizadorNome,
		String tituloCaixinha,
		String confronto,
		String valorIngressoFormatado,
		String linkConvite) {

	public ConviteEmail {
		if (destinatario == null || destinatario.isBlank()) {
			throw new IllegalArgumentException("destinatario é obrigatório");
		}
		if (organizadorNome == null || organizadorNome.isBlank()) {
			throw new IllegalArgumentException("organizadorNome é obrigatório");
		}
		if (tituloCaixinha == null || tituloCaixinha.isBlank()) {
			throw new IllegalArgumentException("tituloCaixinha é obrigatório");
		}
		if (confronto == null || confronto.isBlank()) {
			throw new IllegalArgumentException("confronto é obrigatório");
		}
		if (valorIngressoFormatado == null || valorIngressoFormatado.isBlank()) {
			throw new IllegalArgumentException("valorIngressoFormatado é obrigatório");
		}
		if (linkConvite == null || linkConvite.isBlank()) {
			throw new IllegalArgumentException("linkConvite é obrigatório");
		}
	}
}
