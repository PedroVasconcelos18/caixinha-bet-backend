package com.caxinhabet.auth.adapter.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Payload de {@code POST /auth/recuperar-senha} (auth por senha, 2026-05). */
public record RecuperarSenhaRequest(@NotBlank @Email String email) {}
