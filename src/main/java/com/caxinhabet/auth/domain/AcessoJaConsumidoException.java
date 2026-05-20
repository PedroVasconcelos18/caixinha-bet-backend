package com.caxinhabet.auth.domain;

/**
 * Magic link já usado (uso único — AC-2).
 *
 * <p>Mapeado para HTTP 410 Gone pelo handler em
 * {@code com.caxinhabet.shared.error.GlobalExceptionHandler} (RFC 9457).
 * O front (AC-3) diferencia "expirado" de "já usado" pela mensagem
 * retornada — tom amigável, oferece reenvio (NFR-6).
 */
public class AcessoJaConsumidoException extends RuntimeException {

	public AcessoJaConsumidoException() {
		super("Link já utilizado. Solicite um novo.");
	}
}
