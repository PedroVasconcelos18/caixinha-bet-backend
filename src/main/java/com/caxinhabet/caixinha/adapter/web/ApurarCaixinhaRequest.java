package com.caxinhabet.caixinha.adapter.web;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Corpo de {@code POST /caixinhas/{id}/apuracao} (Story 4.2, FR-12).
 *
 * @param resultadoFinalId id do Resultado Possível escolhido como
 *     Resultado Final (obrigatório).
 * @param ganhadoresEscolhidos ids de Participante que o Organizador
 *     seleciona como Ganhadores — usado SÓ quando há mais palpiteiros
 *     corretos que o Nº de Ganhadores. Pode vir {@code null}/vazio quando
 *     a seleção é automática (corretos ≤ Nº de Ganhadores).
 */
public record ApurarCaixinhaRequest(
		@NotNull Long resultadoFinalId, List<Long> ganhadoresEscolhidos) {}
