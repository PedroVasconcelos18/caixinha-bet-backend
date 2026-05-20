package com.caxinhabet.auth.domain;

/**
 * Magic link expirado (passou de {@code expira_em}).
 *
 * <p>Mapeado para HTTP 410 Gone pelo handler em
 * {@code com.caxinhabet.shared.error.GlobalExceptionHandler} (RFC 9457).
 * 410 é o status semanticamente certo: o recurso (link) existiu e já não
 * existe mais. O front (AC-3) oferece reenvio direto da tela.
 */
public class AcessoExpiradoException extends RuntimeException {

	public AcessoExpiradoException() {
		super("Link expirado. Solicite um novo.");
	}
}
