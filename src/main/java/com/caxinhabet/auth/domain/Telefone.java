package com.caxinhabet.auth.domain;

/**
 * Telefone validado — VO opcional do perfil (Minha Conta, 2026-05).
 *
 * <p>Persistido só com dígitos (10 ou 11 chars — fixo ou celular brasileiro
 * com DDD). Máscara é responsabilidade da UI.
 */
public final class Telefone {

    private final String digitos;

    private Telefone(String digitos) {
        this.digitos = digitos;
    }

    /** Versão obrigatória — lança se inválido. */
    public static Telefone de(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            throw new IllegalArgumentException("Telefone é obrigatório");
        }
        String d = bruto.replaceAll("\\D", "");
        if (d.length() < 10 || d.length() > 11) {
            throw new IllegalArgumentException("Telefone deve ter 10 ou 11 dígitos");
        }
        return new Telefone(d);
    }

    /** Versão opcional — devolve {@code null} se blank, valida senão. */
    public static Telefone deOpcional(String bruto) {
        if (bruto == null || bruto.isBlank()) return null;
        return de(bruto);
    }

    public String digitos() {
        return digitos;
    }
}
