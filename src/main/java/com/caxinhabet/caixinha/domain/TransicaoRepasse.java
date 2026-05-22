package com.caxinhabet.caixinha.domain;

/**
 * Porta de saída: transições de estado da Caixinha durante o Repasse
 * (Épico 4 v5, Story 4.6, FR-13).
 *
 * <p>O {@code AceitarPremioUseCase} vive no módulo {@code pagamento}
 * (é sobre Payout/PIX), mas a transição final {@code repasse_parcial →
 * repassada} é mutação de estado da Caixinha — responsabilidade do
 * módulo {@code caixinha}. Esta porta mantém o isolamento: o módulo
 * {@code pagamento} depende da interface, o adapter de {@code caixinha}
 * a implementa.
 */
public interface TransicaoRepasse {

	/**
	 * Transiciona a Caixinha {@code repasse_parcial → repassada} — chamado
	 * quando TODOS os Ganhadores tiveram o PIX confirmado (Story 4.6).
	 *
	 * <p>Idempotente: se a Caixinha já está {@code repassada}, é no-op.
	 * Se não está em {@code repasse_parcial} (estado inesperado), também
	 * é no-op defensivo — o caller só chama quando todos os Payouts estão
	 * {@code pago}.
	 *
	 * @param caixinhaId Caixinha a finalizar.
	 */
	void marcarRepassada(long caixinhaId);
}
