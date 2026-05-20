package com.caxinhabet.caixinha.domain;

/**
 * Uma das opções de aposta de uma Caixinha (PRD §3 — "Resultado Possível").
 *
 * <p>Mínimo de 2 por Caixinha (FR-1); rótulo não-vazio (após {@code trim}).
 * Ordem é 0-indexed e estável (UNIQUE(caixinha_id, ordem) no DB).
 */
public record ResultadoPossivel(Long id, Long caixinhaId, int ordem, String rotulo) {

	public ResultadoPossivel {
		if (ordem < 0) {
			throw new IllegalArgumentException("ordem deve ser >= 0");
		}
		if (rotulo == null || rotulo.trim().isEmpty()) {
			throw new IllegalArgumentException("rotulo é obrigatório");
		}
		if (rotulo.length() > 120) {
			throw new IllegalArgumentException("rotulo excede 120 chars");
		}
	}
}
