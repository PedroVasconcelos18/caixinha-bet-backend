package com.caxinhabet.auth.domain;

/**
 * Arquivo de foto com tipo (mime) não suportado (Minha Conta, 2026-05).
 * Vira HTTP 415.
 */
public class ArquivoInvalidoException extends RuntimeException {

    public ArquivoInvalidoException(String mensagem) {
        super(mensagem);
    }
}
