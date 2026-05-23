package com.caxinhabet.auth.domain;

/**
 * Arquivo de foto excedeu o limite de tamanho (Minha Conta, 2026-05).
 * Vira HTTP 413.
 */
public class ArquivoMuitoGrandeException extends RuntimeException {

    public ArquivoMuitoGrandeException(String mensagem) {
        super(mensagem);
    }
}
