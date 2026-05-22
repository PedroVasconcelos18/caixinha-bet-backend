package com.caxinhabet.auth.domain;

/**
 * Cadastro com CPF que já tem conta (auth por senha, 2026-05).
 *
 * <p>Mapeada para HTTP 409. {@code type} próprio no ProblemDetails para
 * o front destacar o campo de CPF.
 */
public class CpfJaCadastradoException extends RuntimeException {

	public CpfJaCadastradoException() {
		super("Já existe uma conta com este CPF.");
	}
}
