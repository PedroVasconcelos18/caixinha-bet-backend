package com.caxinhabet.pagamento.domain;

/**
 * Notificação por e-mail de cancelamento da Caixinha (Épico 5 v5,
 * Story 5.1, FR-11 / NFR-6).
 *
 * <p>Tom de cuidado e <b>não-punitivo</b> — nunca "fulano não pagou".
 * "A caixinha não fechou — quem pagou já está sendo reembolsado."
 *
 * @param destinatario e-mail do Participante ou Organizador.
 * @param tituloCaixinha título da Caixinha cancelada.
 * @param houvePagamento {@code true} se o destinatário pagou (texto fala
 *     do reembolso); {@code false} = só informa o cancelamento.
 */
public record CancelamentoEmail(
		String destinatario, String tituloCaixinha, boolean houvePagamento) {

	public CancelamentoEmail {
		if (destinatario == null || destinatario.isBlank()) {
			throw new IllegalArgumentException("destinatario é obrigatório");
		}
		if (tituloCaixinha == null || tituloCaixinha.isBlank()) {
			throw new IllegalArgumentException("tituloCaixinha é obrigatório");
		}
	}
}
