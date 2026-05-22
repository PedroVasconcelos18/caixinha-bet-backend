package com.caxinhabet.caixinha.adapter.web;

/**
 * Resumo do Participante exibido na lista de {@code GET /caixinhas/{id}}
 * (Story 2.2 → 2.5 → 3.5).
 *
 * <p>Story 2.5: incluído {@code palpiteResultadoPossivelId} (nullable).
 * Story 3.5 (painel de transparência): incluído {@code palpiteRotulo} —
 * o rótulo legível do Resultado Possível escolhido (ex.: "Vitória do
 * Brasil"), resolvido no {@code CaixinhaResponse.de} a partir da lista
 * de Resultados. {@code null} se o Participante ainda não palpitou.
 */
public record ParticipanteResumoResponse(
		String email,
		boolean dono,
		String status,
		Long palpiteResultadoPossivelId,
		String palpiteRotulo) {}
