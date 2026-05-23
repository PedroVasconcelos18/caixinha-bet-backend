package com.caxinhabet.auth.domain;

/**
 * Troca de senha logada com a senha atual incorreta (Minha Conta, 2026-05).
 * 401 com {@code type} próprio para o front distinguir de credenciais
 * inválidas do login.
 */
public class SenhaAtualIncorretaException extends RuntimeException {

    public SenhaAtualIncorretaException() {
        super("Senha atual incorreta");
    }
}
