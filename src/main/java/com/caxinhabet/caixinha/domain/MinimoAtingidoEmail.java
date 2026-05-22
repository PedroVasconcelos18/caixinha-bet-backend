package com.caxinhabet.caixinha.domain;

/**
 * Notificação por e-mail aos Participantes quando a Caixinha atinge o
 * Mínimo de Aceites (Story 3.1, FR-6 / NFR-6).
 *
 * <p>Tom NFR-6 (virada/momentum): assunto e corpo curtos, celebratórios
 * mas sem jargão de aposta (guardrail §10). Conteúdo montado pelo use
 * case; este record só carrega os dados.
 *
 * <p>Espelha o padrão de {@link ConviteEmail} da Story 2.4 — porta
 * dedicada, sem reaproveitar a do convite (conteúdo distinto, módulo
 * mesmo).
 *
 * @param destinatario e-mail do Participante que recebe o aviso.
 * @param tituloCaixinha título da Caixinha que acabou de atingir o mínimo.
 * @param confronto rótulo do confronto (ex.: {@code "Brasil × Marrocos"}).
 * @param valorIngressoFormatado valor do ingresso já formatado (ex.: {@code "R$ 40,00"}).
 * @param linkCaixinha URL absoluta para a página de detalhe da Caixinha
 *     (onde o usuário inicia o pagamento — Story 3.2).
 */
public record MinimoAtingidoEmail(
		String destinatario,
		String tituloCaixinha,
		String confronto,
		String valorIngressoFormatado,
		String linkCaixinha) {

	public MinimoAtingidoEmail {
		if (destinatario == null || destinatario.isBlank()) {
			throw new IllegalArgumentException("destinatario é obrigatório");
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
		if (linkCaixinha == null || linkCaixinha.isBlank()) {
			throw new IllegalArgumentException("linkCaixinha é obrigatório");
		}
	}
}
