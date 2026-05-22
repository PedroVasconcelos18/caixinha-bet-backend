package com.caxinhabet.auth.domain;

/**
 * Cadastro com e-mail que já tem conta (auth por senha, 2026-05).
 *
 * <p>Mapeada para HTTP 409. {@code type} próprio no ProblemDetails para
 * o front destacar o campo de e-mail sem inspecionar a mensagem.
 */
public class EmailJaCadastradoException extends RuntimeException {

	public EmailJaCadastradoException() {
		super("Já existe uma conta com este e-mail. Tente entrar.");
	}
}
