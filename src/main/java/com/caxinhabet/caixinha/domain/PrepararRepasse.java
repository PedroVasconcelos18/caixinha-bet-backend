package com.caxinhabet.caixinha.domain;

import java.util.List;

/**
 * Porta de saída: preparar o Repasse do prêmio após a apuração
 * (Épico 4 v5, FR-13 — Story 4.3).
 *
 * <p>O {@code ApurarCaixinhaUseCase} (Story 4.2) vive no módulo
 * {@code caixinha}; a criação dos {@code Payout} e o ledger vivem no
 * módulo {@code pagamento}. Esta porta mantém o isolamento modular
 * (ArquiteturaTest) — a apuração depende da interface, não do adapter.
 *
 * <p>Chamada ao final da apuração, quando há ≥ 1 Ganhador. Em modo
 * reembolso (0 Ganhadores) NÃO é chamada.
 */
public interface PrepararRepasse {

	/**
	 * Calcula os valores, persiste um {@code Payout} por Ganhador,
	 * transiciona a Caixinha {@code apurada → repasse_parcial}, registra a
	 * alocação no ledger e notifica os Ganhadores.
	 *
	 * <p>Não dispara PIX — o disparo é a Story 4.6, após o aceite do
	 * Ganhador.
	 *
	 * @param caixinhaId Caixinha apurada.
	 * @param ganhadoresParticipanteIds ids de Participante dos Ganhadores,
	 *     em ordem de pagamento (o primeiro recebe o resíduo de centavos).
	 */
	void prepararRepasse(long caixinhaId, List<Long> ganhadoresParticipanteIds);
}
