package com.caxinhabet.auth.adapter.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload de {@code POST /auth/verificar-email/reenviar} (Minha Conta,
 * 2026-05). E-mail do usuário que perdeu o link inicial.
 */
public record ReenviarVerificacaoRequest(@NotBlank @Email String email) {}
