package com.caxinhabet.auth.adapter.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload de {@code POST /auth/solicitar-acesso} (Story 2.1, AC-1).
 *
 * <p>{@code redirectTo} é opcional; quando presente, deve ser caminho
 * relativo (a sanitização final acontece no use case). camelCase 1:1
 * com o front (regra dura).
 */
public record SolicitarAcessoRequest(
		@NotBlank @Email String email,
		String redirectTo) {}
