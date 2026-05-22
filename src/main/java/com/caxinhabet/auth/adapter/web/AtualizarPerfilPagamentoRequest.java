package com.caxinhabet.auth.adapter.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload de {@code PUT /auth/me/perfil-pagamento} (Story 3.2 v5).
 *
 * <p>Nome + CPF do Participante — exigidos pelo Asaas para criar o
 * customer. Validação de CPF (dígitos verificadores) é feita no use case
 * {@code AtualizarPerfilPagamentoUseCase}; aqui só o básico de formato.
 *
 * @param nomeCompleto nome do Participante (vira {@code name} no Asaas).
 * @param cpf CPF com ou sem máscara — o use case normaliza para 11 dígitos.
 */
public record AtualizarPerfilPagamentoRequest(
		@NotBlank
		@Size(max = 160)
		@Pattern(
				regexp = "[^\\p{Cntrl}]+",
				message = "nomeCompleto não pode conter caracteres de controle")
		String nomeCompleto,
		@NotBlank @Size(max = 20) String cpf) {}
