package com.caxinhabet.auth.adapter.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Payload de {@code POST /auth/login} (auth por senha, 2026-05). */
public record LoginRequest(
		@NotBlank @Email String email,
		@NotBlank String senha) {}
