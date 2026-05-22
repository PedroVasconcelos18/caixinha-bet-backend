package com.caxinhabet.caixinha.adapter.web;

import com.caxinhabet.caixinha.app.ListarCaixinhasUseCase;
import com.caxinhabet.shared.money.Money;

/**
 * Resumo de uma Caixinha no dashboard — item de {@code GET /caixinhas}
 * (Épico 6 v5, Story 6.1, FR-17).
 *
 * @param id id da Caixinha.
 * @param titulo título.
 * @param confronto rótulo do confronto ({@code "Brasil × Marrocos"}).
 * @param estado estado atual (snake_case).
 * @param pagosConfirmados nº de Participantes que pagaram.
 * @param minimoParticipantes Mínimo de Participantes (denominador do progresso).
 * @param premioPotencial Prêmio potencial (Money string decimal).
 * @param ativa {@code false} se a Caixinha está em estado terminal.
 */
public record CaixinhaResumoResponse(
		long id,
		String titulo,
		String confronto,
		String estado,
		long pagosConfirmados,
		int minimoParticipantes,
		Money premioPotencial,
		boolean ativa) {

	public static CaixinhaResumoResponse de(ListarCaixinhasUseCase.CaixinhaResumo r) {
		return new CaixinhaResumoResponse(
				r.id(),
				r.titulo(),
				r.ladoA() + " × " + r.ladoB(),
				r.estado().name(),
				r.pagosConfirmados(),
				r.minimoParticipantes(),
				r.premioPotencial(),
				r.ativa());
	}
}
