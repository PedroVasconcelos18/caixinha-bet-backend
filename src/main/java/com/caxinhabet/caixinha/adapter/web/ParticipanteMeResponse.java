package com.caxinhabet.caixinha.adapter.web;

/**
 * Visão do PRÓPRIO Participante na tela do convite (Story 2.5).
 *
 * <p>Difere de {@code ParticipanteResumoResponse} (Story 2.2) porque
 * NÃO expõe lista completa de Participantes ao convidado — privacidade.
 * O convidado vê apenas a si mesmo + dados da Caixinha.
 */
public record ParticipanteMeResponse(
		String email, String status, Long palpiteResultadoPossivelId) {}
