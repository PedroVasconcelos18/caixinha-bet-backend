package com.caxinhabet.caixinha.adapter.web;

import java.util.List;

/**
 * Resposta de {@code POST /caixinhas/{id}/convites} (Story 2.4).
 *
 * <p>{@code convidados}: e-mails efetivamente novos, persistidos como
 * Participante e com e-mail disparado (pós-commit). {@code jaPresentes}:
 * e-mails que já eram Participantes (não-erro — informativo).
 */
public record EnviarConvitesResponse(
		List<String> convidados, List<String> jaPresentes) {}
