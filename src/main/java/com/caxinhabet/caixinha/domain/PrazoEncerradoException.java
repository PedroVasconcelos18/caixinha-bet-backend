package com.caxinhabet.caixinha.domain;

/**
 * Lançada quando o Organizador tenta convidar (Story 2.4) ou outra
 * operação dependente do prazo após o {@code prazoEntrada}.
 *
 * <p>Mapeada para HTTP 422 pelo {@code GlobalExceptionHandler} (RFC 9457).
 * 422 = sintaxe OK, semântica inválida (a Caixinha existe, o usuário tem
 * autorização, mas o tempo acabou). A mensagem inclui o prazo formatado.
 */
public class PrazoEncerradoException extends RuntimeException {

	public PrazoEncerradoException(String mensagem) {
		super(mensagem);
	}
}
