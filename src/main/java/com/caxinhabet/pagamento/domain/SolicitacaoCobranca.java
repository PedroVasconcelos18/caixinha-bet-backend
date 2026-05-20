package com.caxinhabet.pagamento.domain;

import com.caxinhabet.shared.money.Money;
import java.util.Objects;

/**
 * Input do domínio para {@link ProvedorPagamento#criarCobranca}.
 *
 * <p>Tipos de domínio puros (NÃO Asaas-específicos): identificadores opacos
 * + {@link Money}. O adapter traduz para o payload do PSP.
 *
 * @param customerId identificador opaco de "quem paga" no PSP (no Asaas:
 *     {@code cus_<hash>}, criado uma vez via {@code POST /v3/customers}). É
 *     o conceito de "cliente cadastrado" do PSP — todos os PSPs reais têm
 *     isso (Stripe, Mercado Pago, etc.). Acrescentado na Story 1.5 quando
 *     descobrimos que o Asaas v3 exige esse campo.
 * @param participanteCaixinhaRef identificador opaco do "quem paga o quê",
 *     formado pelo domínio (ex.: {@code "caixinha-123:participante-456"}).
 *     Vai como {@code externalReference} no PSP — permite correlacionar
 *     o pagamento de volta ao domínio sem o PSP entender o modelo interno.
 * @param valor quanto cobrar — sempre {@link Money} (NUNCA {@code BigDecimal}
 *     cru nem {@code double}/{@code float}; AR-8/NFR-1).
 * @param descricao texto curto mostrado ao pagador (ex.: nome da Caixinha).
 */
public record SolicitacaoCobranca(
		String customerId, String participanteCaixinhaRef, Money valor, String descricao) {

	public SolicitacaoCobranca {
		Objects.requireNonNull(customerId, "customerId");
		Objects.requireNonNull(participanteCaixinhaRef, "participanteCaixinhaRef");
		Objects.requireNonNull(valor, "valor");
		Objects.requireNonNull(descricao, "descricao");
		if (customerId.isBlank()) {
			throw new IllegalArgumentException("customerId não pode ser vazio");
		}
		if (participanteCaixinhaRef.isBlank()) {
			throw new IllegalArgumentException("participanteCaixinhaRef não pode ser vazio");
		}
		if (descricao.isBlank()) {
			throw new IllegalArgumentException("descricao não pode ser vazia");
		}
	}
}
