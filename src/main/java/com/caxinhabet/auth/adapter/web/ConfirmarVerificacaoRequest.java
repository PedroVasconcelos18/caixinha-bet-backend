package com.caxinhabet.auth.adapter.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload de {@code POST /auth/verificar-email/confirmar} (Minha Conta,
 * 2026-05). Token cru da URL do e-mail.
 */
public record ConfirmarVerificacaoRequest(@NotBlank String token) {}
