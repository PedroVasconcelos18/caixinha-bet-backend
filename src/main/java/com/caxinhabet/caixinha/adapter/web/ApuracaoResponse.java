package com.caxinhabet.caixinha.adapter.web;

import com.caxinhabet.caixinha.app.ApurarCaixinhaUseCase;
import java.util.List;

/**
 * Resposta de {@code POST /caixinhas/{id}/apuracao} (Story 4.2, FR-12).
 *
 * @param resultadoFinalId Resultado Final registrado.
 * @param ganhadoresIds ids dos Participantes marcados como Ganhadores
 *     (vazio em modo reembolso).
 * @param modoReembolso {@code true} quando 0 palpiteiros acertaram — o
 *     front exibe o Acerto de Contas em modo reembolso (Story 4.4).
 */
public record ApuracaoResponse(
		long resultadoFinalId, List<Long> ganhadoresIds, boolean modoReembolso) {

	public static ApuracaoResponse de(ApurarCaixinhaUseCase.Resultado r) {
		return new ApuracaoResponse(
				r.resultadoFinalId(), r.ganhadoresIds(), r.modoReembolso());
	}
}
