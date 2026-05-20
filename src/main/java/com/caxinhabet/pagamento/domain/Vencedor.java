package com.caxinhabet.pagamento.domain;

import com.caxinhabet.shared.money.Money;
import java.util.Objects;

/**
 * Entrada para uma linha do {@link ProvedorPagamento#split} (FR-13).
 *
 * <p>Domínio puro — sem qualquer noção de "wallet ID" ou "subconta" do PSP.
 * {@code chavePix} é a chave do destinatário (PSP resolve para conta). Se
 * o vencedor não tiver chave ainda, o domínio pode segurar (crédito retido,
 * OQ-2) e não chamar o split para essa linha.
 *
 * @param chavePix chave PIX do vencedor (CPF/email/telefone/aleatória).
 * @param valor quanto creditar a esse vencedor — {@link Money} (AR-8).
 */
public record Vencedor(String chavePix, Money valor) {

	public Vencedor {
		Objects.requireNonNull(chavePix, "chavePix");
		Objects.requireNonNull(valor, "valor");
		if (chavePix.isBlank()) {
			throw new IllegalArgumentException("chavePix não pode ser vazia");
		}
	}
}
