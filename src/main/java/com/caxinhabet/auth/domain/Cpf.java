package com.caxinhabet.auth.domain;

/**
 * CPF validado — value object do módulo auth (auth por senha, 2026-05).
 *
 * <p>O cadastro grava nome+CPF no Usuário (reusa os campos de perfil de
 * pagamento da migration V8). O CPF precisa ser válido (dígitos
 * verificadores) — este tipo concentra o algoritmo num lugar só, em vez
 * de espalhar pelo use case. {@link #digitos()} devolve sempre 11
 * dígitos, sem máscara — é o formato persistido (coluna {@code cpf}).
 */
public final class Cpf {

    private final String digitos;

    private Cpf(String digitos) {
        this.digitos = digitos;
    }

    /**
     * Cria um CPF a partir de uma string crua (com ou sem máscara).
     *
     * @throws IllegalArgumentException se for nulo/vazio, não tiver 11
     *     dígitos, for sequência repetida, ou tiver dígito verificador
     *     incorreto.
     */
    public static Cpf de(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            throw new IllegalArgumentException("CPF é obrigatório");
        }
        String d = bruto.replaceAll("\\D", "");
        if (d.length() != 11) {
            throw new IllegalArgumentException("CPF deve ter 11 dígitos");
        }
        if (d.chars().distinct().count() == 1) {
            throw new IllegalArgumentException("CPF inválido");
        }
        if (!digitoVerificadorOk(d)) {
            throw new IllegalArgumentException("CPF inválido");
        }
        return new Cpf(d);
    }

    private static boolean digitoVerificadorOk(String d) {
        int d1 = calcularDigito(d, 9, 10);
        int d2 = calcularDigito(d, 10, 11);
        return d1 == (d.charAt(9) - '0') && d2 == (d.charAt(10) - '0');
    }

    private static int calcularDigito(String d, int qtd, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < qtd; i++) {
            soma += (d.charAt(i) - '0') * (pesoInicial - i);
        }
        int resto = 11 - (soma % 11);
        return resto > 9 ? 0 : resto;
    }

    /** CPF normalizado: exatamente 11 dígitos, sem máscara. */
    public String digitos() {
        return digitos;
    }
}
