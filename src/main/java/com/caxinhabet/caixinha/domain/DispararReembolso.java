package com.caxinhabet.caixinha.domain;

/**
 * Porta de saída: disparar o Reembolso de uma Caixinha cancelada
 * (Épico 5 v5, Story 5.1, FR-11).
 *
 * <p>O {@code EncerrarPrazoUseCase} (Story 4.5) vive no módulo
 * {@code caixinha}; o estorno via {@code ProvedorPagamento} e o ledger
 * vivem no módulo {@code pagamento}. Esta porta mantém o isolamento — o
 * cancelamento depende da interface, não do adapter.
 *
 * <p>Chamada quando uma Caixinha é cancelada com Participantes que
 * pagaram. Em cancelamento sem pagamentos ({@code coletando_convites})
 * NÃO é chamada.
 */
public interface DispararReembolso {

	/**
	 * Dispara o estorno de todos os ingressos {@code pago} de uma Caixinha
	 * cancelada. Cada Participante recebe o ingresso integral de volta
	 * (Taxa devolvida — a Caixinha não se realizou).
	 *
	 * <p>Idempotente: cobranças que já não estão {@code confirmada} (já
	 * estornadas) são puladas. O efeito de estado da cobrança é processado
	 * pelo webhook {@code PAYMENT_REFUNDED} (Story 3.3).
	 *
	 * @param caixinhaId Caixinha cancelada.
	 */
	void dispararReembolso(long caixinhaId);
}
