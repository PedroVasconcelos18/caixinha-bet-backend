package com.caxinhabet.pagamento.domain;

import com.caxinhabet.shared.money.Money;
import java.util.Objects;

/**
 * Entrada para o {@link ProvedorPagamento#transferir} (FR-13 v5).
 *
 * <p>Domínio puro — sem qualquer noção de "wallet ID" ou "subconta" do PSP.
 * {@code chavePix} é a chave do destinatário (cadastrada pelo Participante
 * como pré-requisito para palpitar — FR-5 v5). Sem chave válida o payout
 * falha; o app pede correção e mantém o valor retido (C-4 PRD §11).
 *
 * <p>O {@code payoutId} é gerado pelo domínio (não pelo PSP) e usado como
 * chave de idempotência: múltiplas tentativas de transferir o mesmo
 * {@code payoutId} não duplicam o PIX (FR-13 v5).
 *
 * @param payoutId identificador único do payout no domínio (idempotência).
 * @param chavePix chave PIX do Ganhador (CPF/email/telefone/aleatória).
 * @param valor quanto creditar a este Ganhador — {@link Money} (AR-8).
 */
public record Ganhador(String payoutId, String chavePix, Money valor) {

	public Ganhador {
		Objects.requireNonNull(payoutId, "payoutId");
		Objects.requireNonNull(chavePix, "chavePix");
		Objects.requireNonNull(valor, "valor");
		if (payoutId.isBlank()) {
			throw new IllegalArgumentException("payoutId não pode ser vazio");
		}
		if (chavePix.isBlank()) {
			throw new IllegalArgumentException("chavePix não pode ser vazia");
		}
	}
}
