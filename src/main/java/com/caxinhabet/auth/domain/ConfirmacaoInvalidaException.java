package com.caxinhabet.auth.domain;

/**
 * Confirmação textual inválida em operação destrutiva (Minha Conta, 2026-05).
 * Vira HTTP 400 — o front exige digitar "EXCLUIR" e o backend valida de novo.
 */
public class ConfirmacaoInvalidaException extends RuntimeException {

    public ConfirmacaoInvalidaException() {
        super("Confirmação inválida");
    }
}
