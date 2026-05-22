package com.caxinhabet.caixinha.domain;

import java.util.Optional;

/**
 * Porta de leitura da Caixinha exposta a outros módulos (Story 3.2 v5).
 *
 * <p>O módulo {@code pagamento} precisa de alguns dados da Caixinha
 * (estado, valor do ingresso, título) para gerar a cobrança — mas NÃO
 * pode importar {@code caixinha.adapter.persistence} (regra
 * {@code ArquiteturaTest.adapterDeCaixinhaSoConsumidoPorCaixinha}).
 * Esta interface é o contrato: vive no {@code domain}, o adapter de
 * {@code caixinha} implementa, e o consumidor depende só dela.
 *
 * <p>É uma porta de <b>leitura</b> — sem mutação. Mutações de estado da
 * Caixinha continuam internas ao módulo {@code caixinha}.
 */
public interface ConsultaCaixinha {

	/**
	 * Busca os dados de pagamento-relevantes de uma Caixinha.
	 *
	 * @param caixinhaId id da Caixinha.
	 * @return dados, ou {@link Optional#empty()} se a Caixinha não existe.
	 */
	Optional<DadosCaixinha> buscar(long caixinhaId);

	/**
	 * Subconjunto da Caixinha exposto a outros módulos. Tipos de domínio
	 * puro ({@link EstadoCaixinha} é do {@code domain}, livre para outros
	 * módulos) — sem JPA, sem {@code CaixinhaEntity}.
	 *
	 * @param id id da Caixinha.
	 * @param titulo título (mostrado ao pagador na descrição da cobrança).
	 * @param valorIngressoCentavos valor do ingresso em centavos.
	 * @param estado estado atual da Caixinha.
	 */
	record DadosCaixinha(
			long id, String titulo, long valorIngressoCentavos, EstadoCaixinha estado) {}
}
