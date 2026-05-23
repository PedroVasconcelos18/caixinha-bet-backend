package com.caxinhabet.auth.adapter.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload de {@code PUT /auth/me/senha} (Minha Conta, 2026-05).
 *
 * <p>Validação de força da {@code senhaNova} fica no value object {@code Senha}
 * (use case). Aqui só presença.
 */
public record TrocarSenhaRequest(
        @NotBlank String senhaAtual, @NotBlank String senhaNova) {}
