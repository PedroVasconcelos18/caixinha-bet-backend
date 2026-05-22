package com.caxinhabet.pagamento.domain;

import java.util.Objects;

/**
 * Dados mínimos para registrar um "cliente" (quem paga) no PSP
 * (Story 3.2 v5, FR-7).
 *
 * <p>Todos os PSPs reais (Asaas, Stripe, Mercado Pago) exigem cadastrar
 * o pagador antes de cobrar. No Asaas, {@code POST /v3/customers} exige
 * {@code name} + {@code cpfCnpj}. Tipos de domínio puros — o adapter
 * traduz para o payload do PSP.
 *
 * @param nome nome completo do pagador.
 * @param cpf CPF (só dígitos — 11 caracteres; normalização é do caller).
 */
public record DadosCliente(String nome, String cpf) {

	public DadosCliente {
		Objects.requireNonNull(nome, "nome");
		Objects.requireNonNull(cpf, "cpf");
		if (nome.isBlank()) {
			throw new IllegalArgumentException("nome não pode ser vazio");
		}
		if (cpf.length() != 11 || !cpf.chars().allMatch(Character::isDigit)) {
			throw new IllegalArgumentException("cpf deve ter 11 dígitos");
		}
	}
}
