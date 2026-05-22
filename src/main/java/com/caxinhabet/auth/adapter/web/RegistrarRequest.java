package com.caxinhabet.auth.adapter.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload de {@code POST /auth/registrar} (auth por senha, 2026-05).
 *
 * <p>Validação fina (CPF de dígito válido, força da senha) é do use case
 * via value objects {@code Cpf}/{@code Senha}. Aqui só o básico de
 * presença. camelCase 1:1 com o front (regra dura).
 */
public record RegistrarRequest(
		@NotBlank String nomeCompleto,
		@NotBlank String cpf,
		@NotBlank @Email String email,
		@NotBlank String senha) {}
