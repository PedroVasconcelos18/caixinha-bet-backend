package com.caxinhabet.caixinha.adapter.web;

/**
 * Resultado Possível exposto na API (Story 2.2 → expandido Story 2.5).
 *
 * <p>Story 2.5: {@code id} adicionado para o front poder identificar
 * o palpite escolhido em {@code PUT /caixinhas/{id}/palpite}.
 * Adicionar campo é não-breaking (TS aceita campo novo).
 */
public record ResultadoResponse(Long id, int ordem, String rotulo) {}
