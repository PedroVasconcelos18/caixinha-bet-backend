package com.caxinhabet.caixinha.adapter.web;

/**
 * Resumo do Participante exibido na lista de {@code GET /caixinhas/{id}}
 * (Story 2.2 → expandido na Story 2.5).
 *
 * <p>Story 2.5: incluído {@code palpiteResultadoPossivelId}
 * (nullable) para mostrar quem já palpitou na lista de Participantes
 * (Story 6.2 dashboard usa). Adicionar campo a record é não-breaking
 * para clientes (TS interface aceita campo novo).
 */
public record ParticipanteResumoResponse(
		String email, boolean dono, String status, Long palpiteResultadoPossivelId) {}
