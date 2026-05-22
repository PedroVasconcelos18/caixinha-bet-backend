package com.caxinhabet.caixinha.domain;

/**
 * Lançada quando uma operação de pagamento (gerar cobrança, atualizar
 * estado de pagamento) é tentada em uma Caixinha cujo estado ainda não
 * permite (Story 3.1, FR-6).
 *
 * <p>Cenário típico (v5): Caixinha em {@code coletando_convites}
 * (Mínimo de Aceites ainda não atingido). Outros usos futuros: tentar
 * pagar em Caixinha {@code cancelada}, {@code formada}, {@code apurada}.
 *
 * <p>Mapeada para HTTP 422 pelo {@code GlobalExceptionHandler} com type
 * próprio ({@code /problems/pagamento-indisponivel}) — assim o front
 * pode distinguir do palpite/prazo sem inspeção de texto.
 */
public class PagamentoIndisponivelException extends RuntimeException {

	public PagamentoIndisponivelException(String mensagem) {
		super(mensagem);
	}
}
