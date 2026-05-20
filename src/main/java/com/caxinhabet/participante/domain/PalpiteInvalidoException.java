package com.caxinhabet.participante.domain;

/**
 * Lançada quando o Participante tenta definir um palpite inválido
 * (Story 2.5): tipicamente um {@code resultadoPossivelId} que não
 * pertence à própria Caixinha (anti-enumeração de IDs entre Caixinhas).
 *
 * <p>Mapeada para HTTP 422 pelo {@code GlobalExceptionHandler}
 * (RFC 9457). 422 = sintaxe OK, semântica inválida — o id existe na
 * tabela mas não é um Resultado Possível da Caixinha em questão.
 */
public class PalpiteInvalidoException extends RuntimeException {

	public PalpiteInvalidoException(String mensagem) {
		super(mensagem);
	}
}
