package com.caxinhabet.auth.domain;

/**
 * Login com e-mail/senha incorretos (auth por senha, 2026-05).
 *
 * <p>Mapeada para HTTP 401. Mensagem propositalmente genérica
 * ("E-mail ou senha incorretos") — nunca distingue se o e-mail existe
 * (anti-enumeração). Vale também para usuário legado sem senha_hash:
 * a tela de login oferece "Esqueceu a senha?" logo abaixo.
 */
public class CredenciaisInvalidasException extends RuntimeException {

	public CredenciaisInvalidasException() {
		super("E-mail ou senha incorretos.");
	}
}
