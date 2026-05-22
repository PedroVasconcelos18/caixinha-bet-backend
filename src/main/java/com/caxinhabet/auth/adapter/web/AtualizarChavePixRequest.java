package com.caxinhabet.auth.adapter.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload de {@code PUT /auth/me/chave-pix} (Story 2.5 v5).
 *
 * <p>{@code chavePix} pode ser qualquer formato aceito pelo PSP (CPF,
 * e-mail, telefone, chave aleatória). Validação de formato é do PSP, não
 * nossa — ver {@code AtualizarChavePixUseCase}.
 *
 * <p>O {@code @Pattern} aqui rejeita apenas caracteres de controle
 * (LF/CR/TAB/etc.) — não armazena lixo no banco que o PSP rejeitaria
 * depois. Validação de formato real (CPF válido, e-mail bem formado,
 * UUID v4) é responsabilidade do Asaas no payout (FR-13 v5).
 */
public record AtualizarChavePixRequest(
		@NotBlank
		@Size(max = 512)
		@Pattern(
				regexp = "[^\\p{Cntrl}]+",
				message = "chavePix não pode conter caracteres de controle")
		String chavePix) {}
