package com.caxinhabet.auth.domain;

/**
 * Senha crua validada — value object do módulo auth (auth por senha, 2026-05).
 *
 * <p>Representa a senha em texto puro APENAS no momento de cadastrar ou
 * redefinir — o que vai ao banco é o hash BCrypt, nunca este valor.
 * Força mínima ("razoável", alinhada à tela de cadastro): ≥8 caracteres,
 * com pelo menos uma letra e um dígito.
 *
 * <p>Não é persistido nem logado. Existe só na borda do use case, entre
 * receber o request e chamar o {@code PasswordEncoder}.
 */
public final class Senha {

    private static final int TAMANHO_MINIMO = 8;

    private final String valor;

    private Senha(String valor) {
        this.valor = valor;
    }

    /**
     * Cria a partir do texto puro recebido no request.
     *
     * @throws IllegalArgumentException se for nula, curta, ou não tiver
     *     letra+dígito.
     */
    public static Senha crua(String texto) {
        if (texto == null || texto.length() < TAMANHO_MINIMO) {
            throw new IllegalArgumentException(
                    "Senha deve ter pelo menos " + TAMANHO_MINIMO + " caracteres");
        }
        boolean temLetra = texto.chars().anyMatch(Character::isLetter);
        boolean temDigito = texto.chars().anyMatch(Character::isDigit);
        if (!temLetra || !temDigito) {
            throw new IllegalArgumentException(
                    "Senha deve conter letras e números");
        }
        return new Senha(texto);
    }

    /** Texto puro — usar só para passar ao {@code PasswordEncoder.encode}. */
    public String valor() {
        return valor;
    }
}
