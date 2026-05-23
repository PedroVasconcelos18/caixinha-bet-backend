package com.caxinhabet.auth.domain;

/** Login recusado por falta de verificação de e-mail (HTTP 403). */
public class EmailNaoVerificadoException extends RuntimeException {

    private final String email;

    public EmailNaoVerificadoException(String email) {
        super("E-mail não verificado");
        this.email = email;
    }

    public String email() {
        return email;
    }
}
