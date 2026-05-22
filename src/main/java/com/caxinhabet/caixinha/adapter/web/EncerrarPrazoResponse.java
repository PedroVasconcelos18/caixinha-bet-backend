package com.caxinhabet.caixinha.adapter.web;

import com.caxinhabet.caixinha.app.EncerrarPrazoUseCase;

/**
 * Resposta de {@code POST /caixinhas/{id}/encerrar-prazo} (Story 4.5, FR-15).
 *
 * @param estadoResultante estado da Caixinha após o encerramento
 *     (snake_case): {@code formada}, {@code cancelada} ou
 *     {@code coletando_pagamentos} (caso raro da borda Σ ≤ R$ 10).
 * @param reembolsoPendente {@code true} se a Caixinha foi cancelada com
 *     Participantes que pagaram — o Reembolso (Épico 5) será disparado.
 */
public record EncerrarPrazoResponse(
		String estadoResultante, boolean reembolsoPendente) {

	public static EncerrarPrazoResponse de(EncerrarPrazoUseCase.Resultado r) {
		return new EncerrarPrazoResponse(
				r.estadoResultante().name(), r.reembolsoPendente());
	}
}
