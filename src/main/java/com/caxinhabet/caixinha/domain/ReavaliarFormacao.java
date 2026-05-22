package com.caxinhabet.caixinha.domain;

/**
 * Porta de reavaliação da Formação de uma Caixinha (FR-9).
 *
 * <p>O módulo {@code pagamento}, ao processar um webhook que muda o
 * conjunto de Participantes {@code pago} (confirmação ou estorno —
 * Story 3.3), precisa disparar a reavaliação: a Caixinha pode formar
 * ({@code coletando_pagamentos → formada}) ou reverter
 * ({@code formada → coletando_pagamentos}).
 *
 * <p>A lógica de Formação é do módulo {@code caixinha} (estado da
 * Caixinha) — Story 3.4. Esta interface é o contrato: a Story 3.3
 * depende dela; a Story 3.4 fornece a implementação real. Até lá,
 * {@code ReavaliarFormacaoNoOp} satisfaz o contexto Spring sem efeito.
 */
public interface ReavaliarFormacao {

	/**
	 * Reavalia a Formação da Caixinha após uma mudança no conjunto de
	 * pagamentos confirmados.
	 *
	 * @param caixinhaId Caixinha a reavaliar.
	 */
	void reavaliar(long caixinhaId);
}
