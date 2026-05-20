package com.caxinhabet.caixinha.domain;

/**
 * Lançada quando o usuário autenticado tenta uma operação restrita ao
 * dono da Caixinha (Story 2.4: convidar mais Participantes).
 *
 * <p>Mapeada para HTTP 403 pelo {@code GlobalExceptionHandler} (RFC 9457).
 * 403 é correto aqui: usuário identificado MAS não autorizado para esta
 * Caixinha específica.
 */
public class OperacaoNaoAutorizadaException extends RuntimeException {

	public OperacaoNaoAutorizadaException(String mensagem) {
		super(mensagem);
	}
}
