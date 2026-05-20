package com.caxinhabet.caixinha.adapter.web;

import jakarta.validation.constraints.NotNull;

/**
 * Payload de {@code PUT /caixinhas/{id}/palpite} (Story 2.5).
 *
 * <p>Apenas o id do Resultado Possível escolhido. O backend valida que
 * esse id pertence à própria Caixinha (anti-enumeração).
 */
public record DefinirPalpiteRequest(@NotNull Long resultadoPossivelId) {}
