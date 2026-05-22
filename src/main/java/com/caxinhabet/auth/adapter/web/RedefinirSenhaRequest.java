package com.caxinhabet.auth.adapter.web;

import jakarta.validation.constraints.NotBlank;

/** Payload de {@code POST /auth/redefinir-senha} (auth por senha, 2026-05). */
public record RedefinirSenhaRequest(
		@NotBlank String token,
		@NotBlank String senha) {}
