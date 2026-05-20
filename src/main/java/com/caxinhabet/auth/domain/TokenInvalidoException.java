package com.caxinhabet.auth.domain;

/**
 * Token de magic link desconhecido (não há solicitação correspondente).
 *
 * <p>Mapeado para HTTP 404 pelo handler em
 * {@code com.caxinhabet.shared.error.GlobalExceptionHandler} (RFC 9457).
 * A mensagem retornada para o cliente é genérica para não habilitar
 * enumeração de tokens válidos por timing.
 */
public class TokenInvalidoException extends RuntimeException {

	public TokenInvalidoException() {
		super("Token de acesso inválido.");
	}
}
