package com.caxinhabet.caixinha.domain;

import java.util.List;

/**
 * Porta de leitura dos Payouts de uma Caixinha, exposta ao módulo
 * {@code caixinha} (Épico 4 v5, Story 4.4).
 *
 * <p>O Acerto de Contas (módulo {@code caixinha}) precisa do estado do
 * Repasse de cada Ganhador, mas o agregado {@code Payout} vive no módulo
 * {@code pagamento} — {@code caixinha} não pode importar
 * {@code pagamento.adapter}. Esta porta é o contrato de leitura; o
 * adapter de {@code pagamento} a implementa.
 */
public interface ConsultaPayout {

	/**
	 * Payouts de uma Caixinha (vazio se não houver — Caixinha não apurada
	 * ou em modo reembolso).
	 */
	List<DadosPayout> porCaixinha(long caixinhaId);

	/**
	 * Estado do Repasse de um Ganhador, em vocabulário de domínio neutro.
	 *
	 * @param participanteId Participante (Ganhador) do Payout.
	 * @param valorCentavos valor do prêmio em centavos.
	 * @param estado estado do Repasse — ver {@link EstadoRepasse}.
	 * @param comprovante comprovante do PIX ({@code null} enquanto não pago).
	 */
	record DadosPayout(
			long participanteId,
			long valorCentavos,
			EstadoRepasse estado,
			String comprovante) {}

	/**
	 * Estado do Repasse exposto ao Acerto de Contas — espelha o
	 * {@code EstadoPayout} do módulo {@code pagamento}, traduzido para o
	 * domínio de {@code caixinha} (sem acoplar enums entre módulos).
	 */
	enum EstadoRepasse {
		aguardando_aceite,
		pix_em_andamento,
		pago,
		falha_pix
	}
}
