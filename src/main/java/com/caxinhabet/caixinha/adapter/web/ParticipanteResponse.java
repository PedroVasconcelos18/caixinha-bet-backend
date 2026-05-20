package com.caxinhabet.caixinha.adapter.web;

import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;

/**
 * Resposta de {@code POST /aceitar} e {@code PUT /palpite} (Story 2.5).
 * Mostra o Participante atualizado.
 */
public record ParticipanteResponse(
		Long id,
		Long caixinhaId,
		String email,
		String status,
		boolean dono,
		Long palpiteResultadoPossivelId) {

	public static ParticipanteResponse de(ParticipanteEntity p) {
		return new ParticipanteResponse(
				p.getId(),
				p.getCaixinhaId(),
				p.getEmail(),
				p.getStatus().name(),
				p.isDono(),
				p.getPalpiteResultadoPossivelId());
	}
}
