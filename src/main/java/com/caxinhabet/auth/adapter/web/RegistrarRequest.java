package com.caxinhabet.auth.adapter.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Payload de {@code POST /auth/registrar} (Minha Conta, 2026-05).
 *
 * <p>Validação fina (CPF de dígito válido, força da senha, idade ≥ 18) é do
 * use case via value objects {@code Cpf}/{@code Senha}/{@code
 * DataNascimento}. Aqui só presença e o parse ISO de {@link LocalDate}.
 * camelCase 1:1 com o front (regra dura).
 */
public record RegistrarRequest(
		@NotBlank String nomeCompleto,
		@NotBlank String cpf,
		@NotBlank @Email String email,
		@NotBlank String senha,
		@NotNull LocalDate dataNascimento) {}
